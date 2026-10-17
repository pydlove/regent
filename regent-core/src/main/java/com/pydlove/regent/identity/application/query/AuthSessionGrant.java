package com.pydlove.regent.identity.application.query;

import java.time.Instant;

public record AuthSessionGrant(AuthenticatedUser user,
                               String rawToken,
                               Instant expiresAt,
                               boolean remembered) {
}
