package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @Test
    @DisplayName("邮箱统一去空白并转小写")
    void normalizesEmail() {
        Email email = Email.of("  Alice.Example@Example.COM  ");

        assertEquals("alice.example@example.com", email.value());
        assertEquals("alice.example", email.localPart());
    }

    @Test
    @DisplayName("明显错误的邮箱格式直接拒绝")
    void rejectsMalformedEmail() {
        assertThrows(DomainException.class, () -> Email.of(""));
        assertThrows(DomainException.class, () -> Email.of("alice"));
        assertThrows(DomainException.class, () -> Email.of("alice@localhost"));
        assertThrows(DomainException.class, () -> Email.of("alice@@example.com"));
    }
}
