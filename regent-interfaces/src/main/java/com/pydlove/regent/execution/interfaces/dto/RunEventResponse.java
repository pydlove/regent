package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.domain.model.RunEvent;

import java.time.Instant;

/**
 * 执行事件。
 *
 * <p>{@code payload} 是原始 JSON 文本而不是解析后的对象：日志面板的用途是「原样看到
 * 执行器说了什么」，做过加工的日志在排错时会骗人。前端按需自己格式化。
 */
public record RunEventResponse(int seq, String type, String payload, Instant occurredAt) {

    public static RunEventResponse from(RunEvent event) {
        return new RunEventResponse(event.seq(), event.type(), event.payloadJson(), event.occurredAt());
    }
}
