package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;

/**
 * 执行事件流里的一条 —— {@code codex exec --json} 输出的一行。
 *
 * <p>{@code payloadJson} 是原始 JSON 文本而不是解析后的对象：解析是防腐层的职责
 * （{@code infrastructure/execution/codex}），领域层只负责把它按顺序存下来。
 * 一旦让领域层认识 codex 的事件结构，codex 改一次输出格式就要改一次核心域，
 * 那正是端口 + 适配器要避免的事。
 *
 * @param seq         本次运行内的序号，从 0 开始，与 {@code run_event} 的唯一键一致
 * @param type        事件类型，如 {@code thread.started} / {@code item.completed}
 * @param payloadJson 事件原文；非 JSON 行在这里已经被适配器包成 JSON 字符串
 * @param occurredAt  事件时间；解析不出时用落库时刻兜底
 */
public record RunEvent(int seq, String type, String payloadJson, Instant occurredAt) {

    public RunEvent {
        if (seq < 0) {
            throw new DomainException("事件序号不能为负：" + seq);
        }
        if (type == null || type.isBlank()) {
            throw new DomainException("事件类型不能为空");
        }
        if (occurredAt == null) {
            throw new DomainException("事件时间不能为空");
        }
    }
}
