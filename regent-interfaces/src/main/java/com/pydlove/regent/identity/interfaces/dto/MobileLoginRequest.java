package com.pydlove.regent.identity.interfaces.dto;

public record MobileLoginRequest(
        String username,
        String password,
        String installationId) {
}
