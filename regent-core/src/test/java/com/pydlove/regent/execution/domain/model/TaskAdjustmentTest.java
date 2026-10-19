package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskAdjustmentTest {

    private static final TaskId TARGET = TaskId.of(1L);

    @Test
    @DisplayName("临时调整保持待下发，下发时记录执行记录")
    void 临时调整下发() {
        TaskAdjustment adjustment = TaskAdjustment.temporary(TARGET, "  补充错误提示  ");

        assertEquals("补充错误提示", adjustment.getContent());
        assertEquals(TaskAdjustmentSource.TEMPORARY, adjustment.getSource());
        assertEquals(TaskAdjustmentStatus.PENDING, adjustment.getStatus());
        assertTrue(adjustment.getSourceTaskId().isEmpty());

        adjustment.markApplied(TaskRunId.of(9L));

        assertEquals(TaskAdjustmentStatus.APPLIED, adjustment.getStatus());
        assertEquals(TaskRunId.of(9L), adjustment.getAppliedRunId().orElseThrow());
        assertTrue(adjustment.getAppliedAt().isPresent());
    }

    @Test
    @DisplayName("来源任务不能指向目标任务本身")
    void 来源任务不能是自身() {
        assertThrows(DomainException.class,
                () -> TaskAdjustment.fromTask(TARGET, TARGET, "同一个任务", "内容"));
    }

    @Test
    @DisplayName("同一调整不能重复标记下发")
    void 不能重复下发() {
        TaskAdjustment adjustment = TaskAdjustment.temporary(TARGET, "内容");
        adjustment.markApplied(TaskRunId.of(2L));

        assertThrows(DomainException.class, () -> adjustment.markApplied(TaskRunId.of(3L)));
    }

    @Test
    @DisplayName("超长内容与空内容都被拒绝")
    void 内容边界() {
        assertThrows(DomainException.class, () -> TaskAdjustment.temporary(TARGET, " "));
        assertThrows(DomainException.class, () -> TaskAdjustment.temporary(
                TARGET, "x".repeat(TaskAdjustment.MAX_CONTENT_LENGTH + 1)));
    }
}
