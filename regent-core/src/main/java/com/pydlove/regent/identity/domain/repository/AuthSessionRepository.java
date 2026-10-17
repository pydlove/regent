package com.pydlove.regent.identity.domain.repository;

import com.pydlove.regent.identity.domain.model.AuthPrincipal;
import com.pydlove.regent.identity.domain.model.AuthSession;
import com.pydlove.regent.identity.domain.model.SessionTokenHash;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository {

    void save(AuthSession session);

    Optional<AuthPrincipal> findActivePrincipal(SessionTokenHash tokenHash, Instant now);

    void deleteByTokenHash(SessionTokenHash tokenHash);
}
