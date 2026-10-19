package com.pydlove.regent.execution.domain.port;

import com.pydlove.regent.execution.domain.model.ExecutorInstallMethod;
import com.pydlove.regent.execution.domain.model.ExecutorInstallResult;

/** 安装本机执行器。实现只能执行产品预置的固定安装命令。 */
public interface ExecutorInstaller {

    ExecutorInstallResult install(ExecutorInstallMethod method);
}
