package com.pydlove.regent.boot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigEncryptionTest {

    @Test
    @DisplayName("AES-GCM 密文可以正确解回原文")
    void encryptsAndDecrypts() {
        String ciphertext = ConfigEncryption.encrypt("s3cret-value", "test-master-key");

        assertTrue(ciphertext.startsWith("ENC(v1:"));
        assertEquals("s3cret-value", ConfigEncryption.decrypt(ciphertext, "test-master-key"));
    }

    @Test
    @DisplayName("错误主密钥不能解密")
    void rejectsWrongKey() {
        String ciphertext = ConfigEncryption.encrypt("s3cret-value", "test-master-key");

        assertThrows(IllegalStateException.class,
                () -> ConfigEncryption.decrypt(ciphertext, "another-key"));
    }
}
