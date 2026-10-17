package com.pydlove.regent.settings.interfaces.dto;

/** 保存中继连接、安装目录与状态监听开关。密码不经过该接口。 */
public record UpdateRelaySettingsRequest(
        String serverUrl,
        boolean monitoringEnabled,
        String serverIp,
        String sshUsername,
        String installDirectory
) {
}
