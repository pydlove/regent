package com.pydlove.regent.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface EmailVerificationCodeMapper {

    EmailVerificationCodeData findActive(@Param("email") String email,
                                         @Param("now") LocalDateTime now);

    LocalDateTime findLatestCreatedAt(@Param("email") String email);

    long countCreatedSince(@Param("email") String email,
                           @Param("since") LocalDateTime since);

    int insert(EmailVerificationCodeData data);

    int update(EmailVerificationCodeData data);
}
