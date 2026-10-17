package com.pydlove.regent.plan.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanTest {

    private static final Instant NOW = Instant.parse("2026-10-17T08:00:00Z");

    @Test
    @DisplayName("创建计划时去重任务并保留顺序")
    void 创建计划时去重任务并保留顺序() {
        Plan plan = Plan.schedule("发布准备", List.of(
                TaskId.of(3), TaskId.of(1), TaskId.of(3), TaskId.of(2)), NOW.plusSeconds(60));

        assertEquals(List.of(TaskId.of(3), TaskId.of(1), TaskId.of(2)), plan.getTaskIds());
        assertEquals(PlanStatus.SCHEDULED, plan.getStatus());
    }

    @Test
    @DisplayName("计划不能为空且必须设置执行时间")
    void 拒绝空计划() {
        assertThrows(DomainException.class,
                () -> Plan.schedule("空计划", List.of(), NOW));
        assertThrows(DomainException.class,
                () -> Plan.schedule("无时间", List.of(TaskId.of(1)), null));
    }

    @Test
    @DisplayName("定时触发必须到点，人工触发可以提前")
    void 区分定时与人工触发() {
        Plan plan = Plan.schedule("日常检查", List.of(TaskId.of(1)), NOW.plusSeconds(60));

        assertFalse(plan.isDueAt(NOW));
        assertThrows(DomainException.class, () -> plan.start(NOW, PlanTrigger.SCHEDULED));

        plan.start(NOW, PlanTrigger.MANUAL);
        assertEquals(PlanStatus.RUNNING, plan.getStatus());
    }

    @Test
    @DisplayName("计划开始后不能重复开始，完成后不能再开始")
    void 状态迁移带守卫() {
        Plan plan = Plan.schedule("状态守卫", List.of(TaskId.of(1)), NOW);
        plan.start(NOW, PlanTrigger.SCHEDULED);

        assertThrows(DomainException.class, () -> plan.start(NOW, PlanTrigger.MANUAL));

        plan.complete(NOW.plusSeconds(1));
        assertEquals(PlanStatus.COMPLETED, plan.getStatus());
        assertThrows(DomainException.class, () -> plan.complete(NOW.plusSeconds(2)));
    }

    @Test
    @DisplayName("到点边界可以启动计划")
    void 到点边界可以启动计划() {
        Plan plan = Plan.schedule("边界", List.of(TaskId.of(1)), NOW);

        assertTrue(plan.isDueAt(NOW));
        plan.start(NOW, PlanTrigger.SCHEDULED);
        assertEquals(PlanStatus.RUNNING, plan.getStatus());
    }
}
