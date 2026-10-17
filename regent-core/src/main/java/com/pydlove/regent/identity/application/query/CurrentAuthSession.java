package com.pydlove.regent.identity.application.query;

import java.time.Instant;

/** 当前登录状态；用于告诉接口层是否需要续写持久 Cookie。 */
public record CurrentAuthSession(AuthenticatedUser user,
                                 Instant expiresAt,
                                 boolean remembered) {
}
