package com.pydlove.regent.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedTemporaryPasswordGeneratorTest {

    @Test
    void usesProductDefaultPassword() {
        assertEquals("12345678", new FixedTemporaryPasswordGenerator().generate());
    }
}
