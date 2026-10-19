package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.ExecutorInstallMethod;
import com.pydlove.regent.execution.domain.model.ExecutorInstallResult;
import com.pydlove.regent.execution.domain.port.ExecutorInstaller;

/** 一键安装的用例入口。 */
public class ExecutorInstallService {

    private final ExecutorInstaller installer;

    public ExecutorInstallService(ExecutorInstaller installer) {
        this.installer = installer;
    }

    public ExecutorInstallResult install(String method) {
        return installer.install(ExecutorInstallMethod.parse(method));
    }
}
