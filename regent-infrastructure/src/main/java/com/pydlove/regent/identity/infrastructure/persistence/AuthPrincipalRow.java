package com.pydlove.regent.identity.infrastructure.persistence;

import lombok.Data;

@Data
public class AuthPrincipalRow {

    private Long userId;
    private String username;
    private String email;
}
