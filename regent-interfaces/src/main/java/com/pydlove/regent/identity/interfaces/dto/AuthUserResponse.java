package com.pydlove.regent.identity.interfaces.dto;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;

public record AuthUserResponse(
        long id,
        String username,
        String displayName,
        String email,
        String avatarUrl) {

    public static AuthUserResponse from(AuthenticatedUser user) {
        String avatarUrl = user.avatarVersion() == null
                ? null
                : "/api/profile/avatar?v=" + user.avatarVersion();
        return new AuthUserResponse(
                user.id(),
                user.username(),
                user.displayName(),
                user.email(),
                avatarUrl);
    }
}
