package com.pydlove.regent.identity.domain.port;

import com.pydlove.regent.identity.domain.model.Email;

import java.time.Duration;

public interface VerificationEmailSender {

    void sendRegistrationCode(Email email, String code, Duration ttl);
}
