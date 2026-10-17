package com.pydlove.regent.settings.application.query;

/** 设置页读取的中继配置与实时状态。 */
public record RelaySettingsView(
        String serverUrl,
        boolean monitoringEnabled,
        RelayStatusView status
) {
}
