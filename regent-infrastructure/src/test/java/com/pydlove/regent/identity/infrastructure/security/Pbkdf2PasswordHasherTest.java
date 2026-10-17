package com.pydlove.regent.identity.infrastructure.security;

import com.pydlove.regent.identity.domain.model.PasswordHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pbkdf2PasswordHasherTest {

    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher();

    @Test
    @DisplayName("同一密码使用随机盐，编码结果不同但都能校验")
    void hashesWithRandomSaltAndVerifies() {
        PasswordHash first = hasher.hash("safe-pass-123");
        PasswordHash second = hasher.hash("safe-pass-123");

        assertNotEquals(first.value(), second.value());
        assertTrue(hasher.matches("safe-pass-123", first));
        assertFalse(hasher.matches("wrong-pass", first));
    }

    @Test
    @DisplayName("格式损坏或参数异常的哈希按不匹配处理")
    void rejectsMalformedHash() {
        assertFalse(hasher.matches("safe-pass-123", new PasswordHash("not-a-valid-hash")));
        assertFalse(hasher.matches("safe-pass-123", new PasswordHash(
                "pbkdf2-sha256$1$c2FsdA$aGFzaA")));
    }
}
