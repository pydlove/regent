package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/** 外部执行者实时上传的一条原始事件。序号由平台落库时统一分配。 */
public record AgentRunEvent(String type, String payload, Instant occurredAt) {

    private static final int MAX_TYPE_LENGTH = 64;
    private static final int MAX_PAYLOAD_LENGTH = 256 * 1024;

    public AgentRunEvent {
        if (type == null || type.isBlank()) {
            throw new DomainException("事件类型不能为空");
        }
        type = type.trim();
        if (type.length() > MAX_TYPE_LENGTH) {
            throw new DomainException("事件类型过长：" + type.length());
        }
        if (payload == null || payload.isBlank()) {
            throw new DomainException("事件内容不能为空");
        }
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            throw new DomainException("单条事件内容超过 256KB");
        }
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
