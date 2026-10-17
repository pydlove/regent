package com.pydlove.regent.identity.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.AuthPrincipal;
import com.pydlove.regent.identity.domain.model.AuthSession;
import com.pydlove.regent.identity.domain.model.AuthSessionId;
import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.SessionTokenHash;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.domain.model.Username;
import com.pydlove.regent.identity.domain.repository.AuthSessionRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class MyBatisAuthSessionRepository implements AuthSessionRepository {

    private final AuthSessionMapper mapper;

    public MyBatisAuthSessionRepository(AuthSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(AuthSession session) {
        AuthSessionData data = new AuthSessionData();
        data.setUserId(session.getUserId().value());
        data.setTokenHash(session.getTokenHash().value());
        data.setExpiresAt(session.getExpiresAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        data.setCreatedAt(session.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        mapper.insert(data);
        session.assignIdentity(new AuthSessionId(data.getId()));
    }

    @Override
    public Optional<AuthPrincipal> findActivePrincipal(SessionTokenHash tokenHash, Instant now) {
        AuthPrincipalRow row = mapper.findActivePrincipal(
                tokenHash.value(), now.atZone(ZoneOffset.UTC).toLocalDateTime());
        return Optional.ofNullable(row).map(value -> new AuthPrincipal(
                UserId.of(value.getUserId()),
                Username.of(value.getUsername()),
                value.getEmail() == null ? null : Email.of(value.getEmail())));
    }

    @Override
    public void deleteByTokenHash(SessionTokenHash tokenHash) {
        mapper.deleteByTokenHash(tokenHash.value());
    }
}
