package com.pydlove.regent.identity.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.PasswordHash;
import com.pydlove.regent.identity.domain.model.User;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.model.Email;

import java.time.ZoneOffset;

final class UserDataMapper {

    private UserDataMapper() {
    }

    static UserData toData(User user) {
        UserData data = new UserData();
        data.setUsername(user.getUsername().value());
        data.setEmail(user.getEmail() == null ? null : user.getEmail().value());
        data.setPasswordHash(user.getPasswordHash().value());
        data.setCreatedAt(user.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        return data;
    }

    static User toDomain(UserData data) {
        return User.restore(
                UserId.of(data.getId()),
                data.getEmail() == null ? null : Email.of(data.getEmail()),
                Username.of(data.getUsername()),
                new PasswordHash(data.getPasswordHash()),
                data.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
