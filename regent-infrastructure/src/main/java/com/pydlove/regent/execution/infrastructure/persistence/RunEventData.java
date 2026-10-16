package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code run_event} 表的数据对象。
 *
 * <p>{@code payload} 是事件原文。列类型是 JSON，所以写进去之前必须保证是合法 JSON ——
 * 这一步在 {@link MyBatisRunEventRepository} 里做，而不是把责任推给「上游应该给对格式」。
 */
@Data
public class RunEventData {

    private Long id;
    private Long taskRunId;
    private Integer seq;
    private String eventType;
    private String payload;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
