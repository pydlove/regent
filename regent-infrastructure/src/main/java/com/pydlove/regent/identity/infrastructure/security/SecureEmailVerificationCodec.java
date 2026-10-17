package com.pydlove.regent.identity.infrastructure.security;

import com.pydlove.regent.identity.domain.model.EmailVerificationCodeHash;
import com.pydlove.regent.identity.domain.port.EmailVerificationCodec;
import com.pydlove.regent.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/** 生成 6 位数字验证码，并只把其 SHA-256 摘要交给数据库。 */
public class SecureEmailVerificationCodec implements EmailVerificationCodec {

    private static final int CODE_BOUND = 1_000_000;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generateCode() {
        return String.format("%06d", random.nextInt(CODE_BOUND));
    }

    @Override
    public EmailVerificationCodeHash hash(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new DomainException("验证码不能为空");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawCode.getBytes(StandardCharsets.UTF_8));
            return new EmailVerificationCodeHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
