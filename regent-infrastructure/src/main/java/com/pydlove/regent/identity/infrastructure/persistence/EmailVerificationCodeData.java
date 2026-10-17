package com.pydlove.regent.identity.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmailVerificationCodeData {

    private Long id;
    private String email;
    private String codeHash;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private Integer failedAttempts;
    private LocalDateTime createdAt;
}
