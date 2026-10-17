package com.pydlove.regent.identity.interfaces.dto;

import com.pydlove.regent.identity.application.query.AuthenticatedUser;

public record AuthUserResponse(long id, String username, String email) {

    public static AuthUserResponse from(AuthenticatedUser user) {
        return new AuthUserResponse(user.id(), user.username(), user.email());
    }
}
