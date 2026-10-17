package com.pydlove.regent.boot.config;

import com.pydlove.regent.identity.application.command.AuthService;
import com.pydlove.regent.identity.application.command.EmailVerificationService;
import com.pydlove.regent.identity.domain.port.EmailVerificationCodec;
import com.pydlove.regent.identity.domain.port.PasswordHasher;
import com.pydlove.regent.identity.domain.port.SessionTokenCodec;
import com.pydlove.regent.identity.domain.port.VerificationEmailSender;
import com.pydlove.regent.identity.domain.repository.AuthSessionRepository;
import com.pydlove.regent.identity.domain.repository.EmailVerificationCodeRepository;
import com.pydlove.regent.identity.domain.repository.UserRepository;
import com.pydlove.regent.identity.infrastructure.security.Pbkdf2PasswordHasher;
import com.pydlove.regent.identity.infrastructure.security.SecureEmailVerificationCodec;
import com.pydlove.regent.identity.infrastructure.security.SecureSessionTokenCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** 身份认证的显式装配。 */
@Configuration
public class AuthConfig {

    @Bean
    public PasswordHasher passwordHasher() {
        return new Pbkdf2PasswordHasher();
    }

    @Bean
    public SessionTokenCodec sessionTokenCodec() {
        return new SecureSessionTokenCodec();
    }

    @Bean
    public EmailVerificationCodec emailVerificationCodec() {
        return new SecureEmailVerificationCodec();
    }

    @Bean
    public EmailVerificationService emailVerificationService(
            UserRepository userRepository,
            EmailVerificationCodeRepository codeRepository,
            EmailVerificationCodec codec,
            VerificationEmailSender emailSender,
            @Value("${regent.auth.email-code.ttl:PT5M}") Duration codeTtl,
            @Value("${regent.auth.email-code.resend-cooldown:PT60S}") Duration resendCooldown,
            @Value("${regent.auth.email-code.max-per-day:10}") int maxCodesPerDay) {
        return new EmailVerificationService(
                userRepository,
                codeRepository,
                codec,
                emailSender,
                Clock.systemUTC(),
                codeTtl,
                resendCooldown,
                maxCodesPerDay);
    }

    @Bean
    public AuthService authService(UserRepository userRepository,
                                   AuthSessionRepository sessionRepository,
                                   PasswordHasher passwordHasher,
                                   SessionTokenCodec sessionTokenCodec,
                                   EmailVerificationService emailVerificationService,
                                   @Value("${regent.auth.session-ttl:PT168H}") Duration sessionTtl) {
        return new AuthService(
                userRepository,
                sessionRepository,
                passwordHasher,
                sessionTokenCodec,
                emailVerificationService,
                Clock.systemUTC(),
                sessionTtl);
    }
}
