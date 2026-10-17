package com.pydlove.regent.identity.application.command;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.PasswordHash;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T06:00:00Z");
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private InMemoryUserRepository users;
    private InMemoryAttachmentStorage files;
    private UserProfileService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        files = new InMemoryAttachmentStorage();
        service = new UserProfileService(
                users,
                files,
                Clock.fixed(NOW, ZoneOffset.UTC),
                1024);
    }

    @Test
    @DisplayName("显示名称会去除首尾空白并持久化")
    void updatesDisplayName() {
        User user = users.create("alice");

        AuthenticatedUser updated = service.updateDisplayName(user.getId(), "  小艾  ");

        assertEquals("小艾", updated.displayName());
        assertEquals("小艾", users.store.get(user.getId().value()).getDisplayName().value());
        assertEquals("alice", updated.username());
    }

    @Test
    @DisplayName("上传头像会校验图片、落盘并替换旧文件")
    void uploadsAndReplacesAvatar() {
        User user = users.create("alice");

        AuthenticatedUser first = service.uploadAvatar(
                user.getId(),
                new UploadedUserAvatar("first.png", "image/png", PNG));
        String firstPath = users.store.get(user.getId().value()).getAvatar().storagePath();

        assertEquals(NOW.toEpochMilli(), first.avatarVersion());
        assertTrue(firstPath.contains("user_avatar-1"));
        assertArrayEquals(PNG, service.loadAvatar(user.getId()).content());

        AuthenticatedUser second = service.uploadAvatar(
                user.getId(),
                new UploadedUserAvatar("second.png", "image/png", PNG));

        String secondPath = users.store.get(user.getId().value()).getAvatar().storagePath();
        assertNotNull(second.avatarVersion());
        assertTrue(files.stored.containsKey(secondPath));
        assertEquals(1, files.stored.size());
        assertTrue(!files.stored.containsKey(firstPath));
    }

    @Test
    @DisplayName("非图片内容不会落盘，也不会修改用户头像")
    void rejectsInvalidAvatar() {
        User user = users.create("alice");

        assertThrows(DomainException.class, () -> service.uploadAvatar(
                user.getId(),
                new UploadedUserAvatar("fake.png", "image/png", "not-an-image".getBytes())));

        assertTrue(files.stored.isEmpty());
        assertEquals(null, users.store.get(user.getId().value()).getAvatar());
    }

    private static final class InMemoryUserRepository implements UserRepository {

        private final Map<Long, User> store = new LinkedHashMap<>();
        private long sequence;

        private User create(String username) {
            User user = User.register(
                    Email.of(username + "@example.com"),
                    Username.of(username),
                    new PasswordHash("encoded"),
                    NOW);
            user.assignIdentity(UserId.of(++sequence));
            store.put(user.getId().value(), user);
            return user;
        }

        @Override
        public boolean existsByUsername(Username username) {
            return store.values().stream().anyMatch(user -> user.getUsername().equals(username));
        }

        @Override
        public boolean existsByEmail(Email email) {
            return store.values().stream().anyMatch(user -> email.equals(user.getEmail()));
        }

        @Override
        public Optional<User> findByUsername(Username username) {
            return store.values().stream().filter(user -> user.getUsername().equals(username)).findFirst();
        }

        @Override
        public Optional<User> findByEmail(Email email) {
            return store.values().stream().filter(user -> email.equals(user.getEmail())).findFirst();
        }

        @Override
        public Optional<User> findById(UserId id) {
            return Optional.ofNullable(store.get(id.value()));
        }

        @Override
        public void save(User user) {
            store.put(user.getId().value(), user);
        }

        @Override
        public void update(User user) {
            store.put(user.getId().value(), user);
        }
    }

    private static final class InMemoryAttachmentStorage implements AttachmentStorage {

        private final Map<String, byte[]> stored = new LinkedHashMap<>();
        private long sequence;

        @Override
        public String store(AttachmentScope scope, long ownerId, String extension, byte[] content) {
            String path = "/tmp/" + scope.name().toLowerCase() + "-" + ownerId
                    + "/" + (++sequence) + extension;
            stored.put(path, content);
            return path;
        }

        @Override
        public byte[] read(String storagePath) {
            return stored.get(storagePath);
        }

        @Override
        public void delete(String storagePath) {
            stored.remove(storagePath);
        }
    }
}
