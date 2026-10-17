package com.pydlove.regent.settings.interfaces.dto;

/** 一次性 SSH 密码。留空时部署脚本回退到本机 SSH 密钥。 */
public record InstallRelayRequest(String password) {
}
