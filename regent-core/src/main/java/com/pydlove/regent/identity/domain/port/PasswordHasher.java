package com.pydlove.regent.identity.domain.port;

import com.pydlove.regent.identity.domain.model.PasswordHash;

/** 密码编码端口。具体算法属于基础设施实现，领域层不依赖任何安全框架。 */
public interface PasswordHasher {

    PasswordHash hash(String rawPassword);

    boolean matches(String rawPassword, PasswordHash encodedPassword);
}
