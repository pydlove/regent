package com.pydlove.regent.execution.interfaces.dto;

/** 一键安装请求：只允许 npm / brew，具体命令由服务端固定。 */
public record CodexInstallRequest(String method) {
}
