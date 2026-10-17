package com.pydlove.regent.identity.domain.repository;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.EmailVerificationCode;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationCodeRepository {

    Optional<EmailVerificationCode> findActive(Email email, Instant now);

    Optional<Instant> findLatestCreatedAt(Email email);

    long countCreatedSince(Email email, Instant since);

    void save(EmailVerificationCode code);
}
