package com.pydlove.regent.identity.infrastructure.security;

import com.pydlove.regent.identity.domain.model.PasswordHash;
import com.pydlove.regent.identity.domain.port.PasswordHasher;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256 密码编码器。只使用 JDK，避免为了一个哈希算法把安全框架
 * 引入纯领域模块；参数按 OWASP 对 PBKDF2-SHA256 的保守下限设置。
 */
public class Pbkdf2PasswordHasher implements PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String FORMAT = "pbkdf2-sha256";
    private static final int ITERATIONS = 310_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final int MIN_ITERATIONS = 100_000;
    private static final int MAX_ITERATIONS = 1_000_000;

    private final SecureRandom random = new SecureRandom();

    @Override
    public PasswordHash hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] derived = derive(rawPassword.toCharArray(), salt, ITERATIONS);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return new PasswordHash(FORMAT + "$" + ITERATIONS + "$"
                + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derived));
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        String[] parts = encodedPassword.value().split("\\$", -1);
        if (parts.length != 4 || !FORMAT.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
                return false;
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] expected = decoder.decode(parts[3]);
            byte[] actual = derive(rawPassword.toCharArray(), salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalStateException("当前 JDK 不支持 " + ALGORITHM, exception);
        } finally {
            spec.clearPassword();
        }
    }
}
