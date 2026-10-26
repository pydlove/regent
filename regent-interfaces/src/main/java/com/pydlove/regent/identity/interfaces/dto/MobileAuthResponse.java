package com.pydlove.regent.identity.interfaces.dto;

import com.pydlove.regent.identity.application.query.MobileAuthGrant;

import java.time.Instant;

public record MobileAuthResponse(
        AuthUserResponse user,
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        Instant refreshExpiresAt) {

    public static MobileAuthResponse from(MobileAuthGrant grant) {
        return new MobileAuthResponse(
                AuthUserResponse.from(grant.user()),
                grant.accessToken(),
                grant.accessExpiresAt(),
                grant.refreshToken(),
                grant.refreshExpiresAt());
    }
}
