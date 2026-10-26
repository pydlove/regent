package com.pydlove.regent.identity.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MobileAuthSessionData {

    private Long id;
    private Long userId;
    private String installationId;
    private String accessTokenHash;
    private String refreshTokenHash;
    private String previousRefreshTokenHash;
    private LocalDateTime accessExpiresAt;
    private LocalDateTime refreshExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
}
