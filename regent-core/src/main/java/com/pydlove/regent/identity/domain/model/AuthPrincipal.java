package com.pydlove.regent.identity.domain.model;

/** 会话解析出的最小身份信息。历史用户名账号的 email 为空。 */
public record AuthPrincipal(UserId userId, Username username, Email email) {
}
