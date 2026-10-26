package com.pydlove.regent.execution.interfaces.dto;

/** 心跳请求。设备协议同时带上设备号，通用执行者没有设备号时仍按执行者身份续租。 */
public record AgentHeartbeatRequest(String executor, String deviceId) {

    public AgentHeartbeatRequest(String executor) {
        this(executor, null);
    }
}
