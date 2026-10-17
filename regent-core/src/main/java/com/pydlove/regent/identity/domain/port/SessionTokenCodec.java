package com.pydlove.regent.identity.domain.port;

import com.pydlove.regent.identity.domain.model.SessionTokenHash;

/** 生成高熵会话令牌，并计算落库用的单向摘要。 */
public interface SessionTokenCodec {

    String generateRawToken();

    SessionTokenHash hash(String rawToken);
}
