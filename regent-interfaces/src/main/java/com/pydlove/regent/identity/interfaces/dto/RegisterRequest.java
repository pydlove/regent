package com.pydlove.regent.identity.interfaces.dto;

public record RegisterRequest(String email,
                              String emailCode,
                              String password,
                              String confirmPassword) {
}
