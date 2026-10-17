package com.pydlove.regent.identity.application.command;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.application.query.UserAvatarContent;
import com.pydlove.regent.identity.domain.model.DisplayName;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserAvatar;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.kernel.AttachmentScope;
import com.pydlove.regent.shared.kernel.ImageUploadPolicy;

import java.time.Clock;
import java.util.Objects;

/** 用户公开资料的编辑与头像上传。 */
public class UserProfileService {

    private final UserRepository userRepository;
    private final AttachmentStorage attachmentStorage;
    private final Clock clock;
    private final long maxAvatarBytes;

    public UserProfileService(UserRepository userRepository,
                              AttachmentStorage attachmentStorage,
                              Clock clock,
                              long maxAvatarBytes) {
        this.userRepository = Objects.requireNonNull(userRepository, "用户仓储");
        this.attachmentStorage = Objects.requireNonNull(attachmentStorage, "附件存储");
        this.clock = Objects.requireNonNull(clock, "时钟");
        if (maxAvatarBytes <= 0) {
            throw new IllegalArgumentException("头像大小上限必须大于零");
        }
        this.maxAvatarBytes = maxAvatarBytes;
    }

    public AuthenticatedUser updateDisplayName(UserId userId, String displayName) {
        User user = requireUser(userId);
        user.updateDisplayName(DisplayName.of(displayName));
        userRepository.update(user);
        return AuthenticatedUser.from(user);
    }

    public AuthenticatedUser uploadAvatar(UserId userId, UploadedUserAvatar upload) {
        Objects.requireNonNull(upload, "头像上传");
        ImageUploadPolicy.ValidatedImage image = ImageUploadPolicy.validate(
                upload.filename(), upload.contentType(), upload.content(), maxAvatarBytes);
        User user = requireUser(userId);
        UserAvatar previous = user.getAvatar();
        String storagePath = attachmentStorage.store(
                AttachmentScope.USER_AVATAR,
                userId.value(),
                image.extension(),
                image.content());
        UserAvatar avatar = new UserAvatar(
                image.filename(),
                image.contentType(),
                storagePath,
                clock.instant());
        user.changeAvatar(avatar);
        try {
            userRepository.update(user);
        } catch (RuntimeException exception) {
            deleteQuietly(storagePath);
            throw exception;
        }
        if (previous != null) {
            deleteQuietly(previous.storagePath());
        }
        return AuthenticatedUser.from(user);
    }

    public UserAvatarContent loadAvatar(UserId userId) {
        UserAvatar avatar = requireUser(userId).getAvatar();
        if (avatar == null) {
            throw new EntityNotFoundException("该用户还没有上传头像");
        }
        return new UserAvatarContent(avatar.contentType(), attachmentStorage.read(avatar.storagePath()));
    }

    private User requireUser(UserId userId) {
        return userRepository.findById(Objects.requireNonNull(userId, "用户 ID"))
                .orElseThrow(() -> new EntityNotFoundException("用户不存在：" + userId.value()));
    }

    private void deleteQuietly(String storagePath) {
        try {
            attachmentStorage.delete(storagePath);
        } catch (RuntimeException ignored) {
            // 头像已经正确落库或补偿结束时，残留旧文件不应让用户看到一个失败结果。
        }
    }
}
