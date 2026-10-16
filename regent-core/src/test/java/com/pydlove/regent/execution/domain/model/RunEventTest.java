package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 执行事件的基本校验。
 *
 * <p>序号从 0 开始且非负，是 {@code uk_event_run_seq} 唯一键在内存侧的第一道守卫：
 * 让负数或空白类型混进来，撞唯一键的报错会发生在写库那一刻，而那时已经很难追出是谁造的。
 */
class RunEventTest {

    private static final Instant NOW = Instant.parse("2026-10-16T02:00:00Z");

    @Test
    void 序号从零开始且可以没有内容() {
        RunEvent event = assertDoesNotThrow(() -> new RunEvent(0, "turn.started", null, NOW));

        assertEquals(0, event.seq());
        assertEquals("turn.started", event.type());
    }

    @Test
    void 序号不能为负() {
        assertThrows(DomainException.class, () -> new RunEvent(-1, "turn.started", null, NOW));
    }

    @Test
    void 事件类型不能为空() {
        assertThrows(DomainException.class, () -> new RunEvent(0, "  ", null, NOW));
    }

    @Test
    void 事件时间必须存在() {
        assertThrows(DomainException.class, () -> new RunEvent(0, "turn.started", null, null));
    }
}
