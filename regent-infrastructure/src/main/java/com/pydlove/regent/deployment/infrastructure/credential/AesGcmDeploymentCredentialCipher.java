package com.pydlove.regent.deployment.infrastructure.credential;

import com.pydlove.regent.shared.domain.DomainException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** 使用本机主密钥加密部署密码。密文格式：{@code v1.<iv>.<ciphertext>}。 */
final class AesGcmDeploymentCredentialCipher {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "v1.";

    private final Path keyFile;
    private final SecureRandom random = new SecureRandom();
    private volatile byte[] key;

    AesGcmDeploymentCredentialCipher(Path keyFile) {
        this.keyFile = keyFile;
    }

    /** ownerId 是 AAD 绑定 ID；新建服务器凭据使用服务器 ID，迁移凭据沿用旧部署 ID。 */
    String encrypt(long ownerId, String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, ownerId, iv);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getEncoder();
            return PREFIX + encoder.encodeToString(iv) + "." + encoder.encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new DomainException("部署密码加密失败", e);
        }
    }

    String decrypt(long ownerId, String encoded) {
        if (encoded == null || !encoded.startsWith(PREFIX)) {
            throw new DomainException("部署密码密文版本无法识别");
        }
        String[] parts = encoded.substring(PREFIX.length()).split("\\.", 2);
        if (parts.length != 2) {
            throw new DomainException("部署密码密文格式不正确");
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] iv = decoder.decode(parts[0]);
            byte[] encrypted = decoder.decode(parts[1]);
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, ownerId, iv);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new DomainException("部署密码解密失败，请重新保存密码", e);
        }
    }

    private Cipher cipher(int mode, long ownerId, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,
                new SecretKeySpec(key(), "AES"),
                new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(("deployment:" + ownerId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private byte[] key() {
        byte[] current = key;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (key == null) {
                key = loadOrCreateKey();
            }
            return key;
        }
    }

    private byte[] loadOrCreateKey() {
        try {
            Path parent = keyFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
                restrictPermissions(parent, "rwx------");
            }
            if (!Files.exists(keyFile)) {
                byte[] generated = new byte[KEY_BYTES];
                random.nextBytes(generated);
                try {
                    Files.writeString(keyFile,
                            Base64.getEncoder().encodeToString(generated),
                            StandardCharsets.US_ASCII,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                } catch (FileAlreadyExistsException ignored) {
                    // 另一线程或进程先生成；下面按已有文件读取
                }
                restrictPermissions(keyFile, "rw-------");
            }
            byte[] decoded = Base64.getDecoder().decode(Files.readString(
                    keyFile, StandardCharsets.US_ASCII).trim());
            if (decoded.length != KEY_BYTES) {
                throw new DomainException("部署密码主密钥长度不正确，请检查 " + keyFile);
            }
            return decoded;
        } catch (IOException | IllegalArgumentException e) {
            throw new DomainException("无法读取部署密码主密钥：" + keyFile, e);
        }
    }

    private static void restrictPermissions(Path path, String permissions) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (UnsupportedOperationException | IOException ignored) {
            // 非 POSIX 文件系统仍可使用；正式运行环境是 macOS/Linux
        }
    }
}
