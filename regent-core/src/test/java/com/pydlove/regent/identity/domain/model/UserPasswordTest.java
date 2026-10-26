package com.pydlove.regent.identity.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPasswordTest {

    @Test
    @org.junit.jupiter.api.DisplayName("管理员创建的临时账号修改密码后解除首次登录限制")
    void temporaryPasswordMustBeChanged() {
        User user = User.registerTemporary(
                Email.of("new.member@example.com"),
                Username.of("new-member"),
                new DisplayName("新成员"),
                new PasswordHash("initial-hash"),
                Instant.parse("2026-10-23T08:00:00Z"));

        assertTrue(user.mustChangePassword());

        user.changePassword(new PasswordHash("new-hash"));

        assertFalse(user.mustChangePassword());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("管理员重置密码后重新要求首次登录改密")
    void resetPasswordRequiresAnotherChange() {
        User user = User.registerTemporary(
                Email.of("reset.member@example.com"),
                Username.of("reset-member"),
                new DisplayName("重置成员"),
                new PasswordHash("initial-hash"),
                Instant.parse("2026-10-23T08:00:00Z"));
        user.changePassword(new PasswordHash("personal-hash"));
        assertFalse(user.mustChangePassword());

        user.resetTemporaryPassword(new PasswordHash("reset-hash"));

        assertTrue(user.mustChangePassword());
    }
}
