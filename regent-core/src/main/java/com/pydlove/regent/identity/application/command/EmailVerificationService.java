package com.pydlove.regent.identity.application.command;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.EmailVerificationCode;
import com.pydlove.regent.identity.domain.port.EmailVerificationCodec;
import com.pydlove.regent.identity.domain.port.VerificationEmailSender;
import com.pydlove.regent.identity.domain.repository.EmailVerificationCodeRepository;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 邮箱验证码的签发、频控、校验与一次性消费。 */
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailVerificationCodeRepository codeRepository;
    private final EmailVerificationCodec codec;
    private final VerificationEmailSender emailSender;
    private final Clock clock;
    private final Duration codeTtl;
    private final Duration resendCooldown;
    private final int maxCodesPerDay;

    public EmailVerificationService(UserRepository userRepository,
                                    EmailVerificationCodeRepository codeRepository,
                                    EmailVerificationCodec codec,
                                    VerificationEmailSender emailSender,
                                    Clock clock,
                                    Duration codeTtl,
                                    Duration resendCooldown,
                                    int maxCodesPerDay) {
        this.userRepository = Objects.requireNonNull(userRepository, "用户仓储");
        this.codeRepository = Objects.requireNonNull(codeRepository, "验证码仓储");
        this.codec = Objects.requireNonNull(codec, "验证码编解码器");
        this.emailSender = Objects.requireNonNull(emailSender, "验证码邮件发送器");
        this.clock = Objects.requireNonNull(clock, "时钟");
        this.codeTtl = requirePositive(codeTtl, "验证码有效期");
        this.resendCooldown = requirePositive(resendCooldown, "验证码重发间隔");
        if (maxCodesPerDay < 1) {
            throw new DomainException("每日验证码发送上限必须大于零");
        }
        this.maxCodesPerDay = maxCodesPerDay;
    }

    public void sendRegistrationCode(String rawEmail) {
        Email email = Email.of(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new DomainException("该邮箱已注册，请直接登录");
        }

        Instant now = clock.instant();
        codeRepository.findLatestCreatedAt(email).ifPresent(latest -> {
            if (latest.plus(resendCooldown).isAfter(now)) {
                throw new DomainException("验证码发送过于频繁，请稍后再试");
            }
        });
        if (codeRepository.countCreatedSince(email, now.minus(Duration.ofDays(1))) >= maxCodesPerDay) {
            throw new DomainException("该邮箱今日验证码发送次数已达上限，请明天再试");
        }

        String rawCode = codec.generateCode();
        emailSender.sendRegistrationCode(email, rawCode, codeTtl);

        codeRepository.findActive(email, now).ifPresent(active -> {
            active.invalidate(now);
            codeRepository.save(active);
        });
        EmailVerificationCode challenge = EmailVerificationCode.issue(
                email, codec.hash(rawCode), now, codeTtl);
        codeRepository.save(challenge);
    }

    public void verifyRegistrationCode(Email email, String rawCode) {
        if (rawCode == null || !rawCode.trim().matches("\\d{6}")) {
            throw new DomainException("请输入 6 位邮箱验证码");
        }

        Instant now = clock.instant();
        EmailVerificationCode challenge = codeRepository.findActive(email, now)
                .orElseThrow(() -> new DomainException("验证码已失效，请重新获取"));
        boolean verified = challenge.verify(codec.hash(rawCode.trim()), now);
        codeRepository.save(challenge);
        if (verified) {
            return;
        }
        if (!challenge.isActive(now)) {
            throw new DomainException("验证码错误次数过多，请重新获取");
        }
        throw new DomainException("邮箱验证码错误");
    }

    private static Duration requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new DomainException(label + "必须大于零");
        }
        return value;
    }
}
