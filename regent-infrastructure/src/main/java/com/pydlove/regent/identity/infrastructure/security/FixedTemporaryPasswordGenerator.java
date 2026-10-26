package com.pydlove.regent.identity.infrastructure.security;

import com.pydlove.regent.identity.domain.port.TemporaryPasswordGenerator;

/** 产品约定的统一初始密码；首次登录必须修改，不能作为长期密码使用。 */
public class FixedTemporaryPasswordGenerator implements TemporaryPasswordGenerator {

    public static final String DEFAULT_PASSWORD = "12345678";

    @Override
    public String generate() {
        return DEFAULT_PASSWORD;
    }
}
