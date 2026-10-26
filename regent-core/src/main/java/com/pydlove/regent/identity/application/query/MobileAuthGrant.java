package com.pydlove.regent.identity.application.query;

import java.time.Instant;

public record MobileAuthGrant(
        AuthenticatedUser user,
        String accessToken,
        Instant accessExpiresAt,
        String refreshToken,
        Instant refreshExpiresAt) {
}
