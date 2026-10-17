package com.pydlove.regent.identity.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.EmailVerificationCode;
import com.pydlove.regent.identity.domain.model.EmailVerificationCodeHash;

import java.time.ZoneOffset;

final class EmailVerificationCodeDataMapper {

    private EmailVerificationCodeDataMapper() {
    }

    static EmailVerificationCodeData toData(EmailVerificationCode code) {
        EmailVerificationCodeData data = new EmailVerificationCodeData();
        data.setId(code.getId());
        data.setEmail(code.getEmail().value());
        data.setCodeHash(code.getCodeHash().value());
        data.setExpiresAt(code.getExpiresAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        data.setConsumedAt(code.getConsumedAt() == null
                ? null
                : code.getConsumedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        data.setFailedAttempts(code.getFailedAttempts());
        data.setCreatedAt(code.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime());
        return data;
    }

    static EmailVerificationCode toDomain(EmailVerificationCodeData data) {
        return EmailVerificationCode.restore(
                data.getId(),
                Email.of(data.getEmail()),
                new EmailVerificationCodeHash(data.getCodeHash()),
                data.getCreatedAt().toInstant(ZoneOffset.UTC),
                data.getExpiresAt().toInstant(ZoneOffset.UTC),
                data.getConsumedAt() == null
                        ? null
                        : data.getConsumedAt().toInstant(ZoneOffset.UTC),
                data.getFailedAttempts());
    }
}
