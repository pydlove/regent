package com.pydlove.regent.identity.interfaces.dto;

import java.time.Instant;

/** 设备代理长期令牌。明文只在这一响应里出现一次，设备端应安全保存。 */
public record DeviceSessionResponse(String token, Instant expiresAt) {
}
