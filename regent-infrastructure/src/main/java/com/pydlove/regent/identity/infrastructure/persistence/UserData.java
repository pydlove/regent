package com.pydlove.regent.identity.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserData {

    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private LocalDateTime createdAt;
}
