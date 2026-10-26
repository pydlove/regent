package com.pydlove.regent.identity.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserData {

    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String passwordHash;
    private Boolean mustChangePassword;
    private String avatarFilename;
    private String avatarContentType;
    private String avatarStoragePath;
    private LocalDateTime avatarUpdatedAt;
    private LocalDateTime createdAt;
}
