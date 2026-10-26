package com.pydlove.regent.identity.interfaces.dto;

public record ChangePasswordRequest(
        String currentPassword,
        String newPassword,
        String confirmPassword) {
}
