package com.pydlove.regent.settings.interfaces.dto;

/** 保存中继服务器地址与状态监听开关。共享密钥不经过该接口。 */
public record UpdateRelaySettingsRequest(String serverUrl, boolean monitoringEnabled) {
}
