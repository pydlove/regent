package com.pydlove.regent.settings.application.query;

/** 一次中继安装操作的结果。 */
public record RelayInstallView(
        boolean success,
        String installDirectory,
        String message
) {
}
