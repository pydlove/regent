package com.pydlove.regent.deployment.infrastructure.credential;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AesGcmDeploymentCredentialCipherTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("密码使用随机 IV 加密，并可绑定部署 ID 解密")
    void encryptsAndDecryptsPerDeployment() throws Exception {
        Path keyFile = temp.resolve("credential.key");
        AesGcmDeploymentCredentialCipher cipher = new AesGcmDeploymentCredentialCipher(keyFile);

        String first = cipher.encrypt(41L, "s3cret");
        String second = cipher.encrypt(41L, "s3cret");

        assertNotEquals(first, second);
        assertEquals("s3cret", cipher.decrypt(41L, first));
        assertThrows(DomainException.class, () -> cipher.decrypt(42L, first));
        assertEquals(32, Base64.getDecoder().decode(
                Files.readString(keyFile).trim()).length);
    }
}
