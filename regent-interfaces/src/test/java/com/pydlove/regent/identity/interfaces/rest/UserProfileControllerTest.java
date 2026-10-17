package com.pydlove.regent.identity.interfaces.rest;

import com.pydlove.regent.identity.application.command.UploadedUserAvatar;
import com.pydlove.regent.identity.application.command.UserProfileService;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.application.query.UserAvatarContent;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileControllerTest {

    private StubProfileService profileService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        profileService = new StubProfileService();
        mockMvc = MockMvcBuilders.standaloneSetup(
                new UserProfileController(profileService)).build();
    }

    @Test
    @DisplayName("编辑资料只更新当前用户的显示名称")
    void updatesCurrentUserDisplayName() throws Exception {
        profileService.updatedUser = new AuthenticatedUser(
                7, "alice", "小艾", "alice@example.com", 1234L);

        mockMvc.perform(put("/api/profile")
                        .requestAttr(AuthSessionInterceptor.CURRENT_USER_ATTRIBUTE, currentUser())
                        .contentType("application/json")
                        .content("""
                                {"displayName":"小艾"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.displayName").value("小艾"))
                .andExpect(jsonPath("$.avatarUrl").value("/api/profile/avatar?v=1234"));
    }

    @Test
    @DisplayName("头像上传把原始文件内容交给资料服务")
    void uploadsAvatar() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        profileService.updatedUser = new AuthenticatedUser(
                7, "alice", "Alice", "alice@example.com", 5678L);

        mockMvc.perform(multipart("/api/profile/avatar")
                        .file(file)
                        .requestAttr(AuthSessionInterceptor.CURRENT_USER_ATTRIBUTE, currentUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("/api/profile/avatar?v=5678"));

        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new byte[]{1, 2, 3}, profileService.lastUpload.content());
    }

    @Test
    @DisplayName("头像内容使用私有缓存并按更新时间生成版本地址")
    void servesCurrentUserAvatar() throws Exception {
        profileService.avatar = new UserAvatarContent("image/png", new byte[]{1, 2, 3});

        mockMvc.perform(get("/api/profile/avatar")
                        .requestAttr(AuthSessionInterceptor.CURRENT_USER_ATTRIBUTE, currentUser()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("Cache-Control", containsString("private")));
    }

    private static AuthenticatedUser currentUser() {
        return new AuthenticatedUser(7, "alice", "Alice", "alice@example.com", 1234L);
    }

    private static final class StubProfileService extends UserProfileService {

        private AuthenticatedUser updatedUser;
        private UploadedUserAvatar lastUpload;
        private UserAvatarContent avatar;

        private StubProfileService() {
            super(
                    new EmptyUserRepository(),
                    new EmptyAttachmentStorage(),
                    Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                    1024);
        }

        @Override
        public AuthenticatedUser updateDisplayName(UserId userId, String displayName) {
            return updatedUser;
        }

        @Override
        public AuthenticatedUser uploadAvatar(UserId userId, UploadedUserAvatar upload) {
            lastUpload = upload;
            return updatedUser;
        }

        @Override
        public UserAvatarContent loadAvatar(UserId userId) {
            return avatar;
        }
    }

    private static final class EmptyUserRepository implements UserRepository {

        @Override
        public boolean existsByUsername(Username username) {
            return false;
        }

        @Override
        public boolean existsByEmail(Email email) {
            return false;
        }

        @Override
        public Optional<User> findByUsername(Username username) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return Optional.empty();
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.empty();
        }

        @Override
        public void save(User user) {
        }

        @Override
        public void update(User user) {
        }
    }

    private static final class EmptyAttachmentStorage implements AttachmentStorage {

        @Override
        public String store(AttachmentScope scope, long ownerId, String extension, byte[] content) {
            return "";
        }

        @Override
        public byte[] read(String storagePath) {
            return new byte[0];
        }

        @Override
        public void delete(String storagePath) {
        }
    }
}
