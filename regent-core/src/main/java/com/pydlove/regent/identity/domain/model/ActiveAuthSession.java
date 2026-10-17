package com.pydlove.regent.identity.domain.model;

import java.util.Objects;

/** 会话校验通过后的会话实体与用户身份投影。 */
public record ActiveAuthSession(AuthSession session, AuthPrincipal principal) {

    public ActiveAuthSession {
        Objects.requireNonNull(session, "会话");
        Objects.requireNonNull(principal, "会话身份");
    }
}
