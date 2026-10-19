package com.pydlove.regent.boot.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置文件敏感值的 AES-256-GCM 加解密。
 *
 * <p>密文格式为 {@code ENC(v1:<base64url(iv + ciphertext + tag)>)}。主密钥不写进配置文件：
 * 优先读取 {@code REGENT_CONFIG_KEY} 环境变量，其次读取 {@code ~/.regent/config.key}。
 * 这个类只依赖 JDK，方便在启动前用命令行工具生成密文。
 */
public final class ConfigEncryption {

    private static final String PREFIX = "ENC(v1:";
    private static final String SUFFIX = ")";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ConfigEncryption() {
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX) && value.endsWith(SUFFIX);
    }

    public static String encrypt(String plaintext, String masterKey) {
        if (plaintext == null) {
            throw new IllegalArgumentException("待加密内容不能为空");
        }
        byte[] key = deriveKey(masterKey);
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(iv.length + ciphertext.length);
            payload.put(iv).put(ciphertext);
            return PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.array()) + SUFFIX;
        } catch (Exception e) {
            throw new IllegalStateException("配置加密失败", e);
        }
    }

    public static String decrypt(String encrypted, String masterKey) {
        if (!isEncrypted(encrypted)) {
            throw new IllegalArgumentException("不是受支持的配置密文格式");
        }
        byte[] key = deriveKey(masterKey);
        try {
            byte[] payload = Base64.getUrlDecoder().decode(
                    encrypted.substring(PREFIX.length(), encrypted.length() - SUFFIX.length()));
            if (payload.length <= IV_BYTES + TAG_BITS / 8) {
                throw new IllegalArgumentException("配置密文长度不正确");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "配置解密失败，请确认 REGENT_CONFIG_KEY 或 ~/.regent/config.key 未变化且密文完整", e);
        }
    }

    /** 供启动器和命令行工具共用的主密钥读取顺序：环境变量 → JVM 参数 → 用户目录密钥文件。 */
    public static String resolveMasterKey() {
        String fromEnvironment = System.getenv("REGENT_CONFIG_KEY");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment.trim();
        }
        String fromProperty = System.getProperty("regent.config.key");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        Path keyFile = Path.of(System.getProperty("user.home"), ".regent", "config.key");
        if (!Files.isRegularFile(keyFile)) {
            return null;
        }
        try {
            String value = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
            return value.isBlank() ? null : value;
        } catch (IOException e) {
            throw new IllegalStateException("无法读取配置主密钥文件：" + keyFile, e);
        }
    }

    private static byte[] deriveKey(String masterKey) {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalArgumentException("配置主密钥不能为空");
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(masterKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }
}
