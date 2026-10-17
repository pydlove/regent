package com.pydlove.regent.identity.domain.port;

import com.pydlove.regent.identity.domain.model.EmailVerificationCodeHash;

public interface EmailVerificationCodec {

    String generateCode();

    EmailVerificationCodeHash hash(String rawCode);
}
