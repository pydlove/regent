package com.pydlove.regent.identity.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.DisplayName;
import com.pydlove.regent.identity.domain.model.PasswordHash;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserAvatar;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.model.Email;

import java.time.ZoneOffset;

final class UserDataMapper {

    private UserDataMapper() {
    }

    static UserData toData(User user) {
        UserData data = new UserData();
        data.setId(user.getId() == null ? null : user.getId().value());
        data.setUsername(user.getUsername().value());
        data.setDisplayName(user.getDisplayName().value());
        data.setEmail(user.getEmail() == null ? null : user.getEmail().value());
        data.setPasswordHash(user.getPasswordHash().value());
        if (user.getAvatar() != null) {
            data.setAvatarFilename(user.getAvatar().filename());
            data.setAvatarContentType(user.getAvatar().contentType());
            data.setAvatarStoragePath(user.getAvatar().storagePath());
            data.setAvatarUpdatedAt(
                    user.getAvatar().updatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        }
        data.setCreatedAt(user.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        return data;
    }

    static User toDomain(UserData data) {
        return User.restore(
                UserId.of(data.getId()),
                data.getEmail() == null ? null : Email.of(data.getEmail()),
                Username.of(data.getUsername()),
                DisplayName.of(data.getDisplayName()),
                new PasswordHash(data.getPasswordHash()),
                data.getAvatarStoragePath() == null
                        ? null
                        : new UserAvatar(
                                data.getAvatarFilename(),
                                data.getAvatarContentType(),
                                data.getAvatarStoragePath(),
                                data.getAvatarUpdatedAt().toInstant(ZoneOffset.UTC)),
                data.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
