package com.pydlove.regent.settings.interfaces.dto;

import com.pydlove.regent.settings.application.query.RelayInstallView;

/** 中继安装结果。 */
public record RelayInstallResponse(
        boolean success,
        String installDirectory,
        String message
) {
    public static RelayInstallResponse from(RelayInstallView view) {
        return new RelayInstallResponse(
                view.success(),
                view.installDirectory(),
                view.message()
        );
    }
}
