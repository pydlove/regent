package com.pydlove.regent.identity.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuthSessionData {

    private Long id;
    private Long userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
