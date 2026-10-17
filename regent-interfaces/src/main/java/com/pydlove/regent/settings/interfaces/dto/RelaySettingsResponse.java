package com.pydlove.regent.settings.interfaces.dto;

import com.pydlove.regent.settings.application.query.RelaySettingsView;

/** 中继设置及其实时状态的前端契约。 */
public record RelaySettingsResponse(
        String serverUrl,
        boolean monitoringEnabled,
        RelayStatusResponse status
) {
    public static RelaySettingsResponse from(RelaySettingsView view) {
        return new RelaySettingsResponse(
                view.serverUrl(),
                view.monitoringEnabled(),
                RelayStatusResponse.from(view.status())
        );
    }
}
