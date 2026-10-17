package com.pydlove.regent.identity.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.EmailVerificationCode;
import com.pydlove.regent.identity.domain.repository.EmailVerificationCodeRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class MyBatisEmailVerificationCodeRepository implements EmailVerificationCodeRepository {

    private final EmailVerificationCodeMapper mapper;

    public MyBatisEmailVerificationCodeRepository(EmailVerificationCodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<EmailVerificationCode> findActive(Email email, Instant now) {
        return Optional.ofNullable(mapper.findActive(email.value(), local(now)))
                .map(EmailVerificationCodeDataMapper::toDomain);
    }

    @Override
    public Optional<Instant> findLatestCreatedAt(Email email) {
        return Optional.ofNullable(mapper.findLatestCreatedAt(email.value()))
                .map(value -> value.toInstant(ZoneOffset.UTC));
    }

    @Override
    public long countCreatedSince(Email email, Instant since) {
        return mapper.countCreatedSince(email.value(), local(since));
    }

    @Override
    public void save(EmailVerificationCode code) {
        EmailVerificationCodeData data = EmailVerificationCodeDataMapper.toData(code);
        if (code.getId() == null) {
            mapper.insert(data);
            code.assignIdentity(data.getId());
        } else {
            mapper.update(data);
        }
    }

    private static LocalDateTime local(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDateTime();
    }
}
