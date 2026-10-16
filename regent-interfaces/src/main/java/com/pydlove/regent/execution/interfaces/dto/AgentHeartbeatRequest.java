package com.pydlove.regent.execution.interfaces.dto;

/** 心跳请求。只有执行者名字：租约是发给具体某个执行者的，不能随便替别人续。 */
public record AgentHeartbeatRequest(String executor) {
}
