package com.pydlove.regent.plan.domain.model;

import com.pydlove.regent.execution.domain.model.TaskId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanRunTest {

    private final PlanRun run = PlanRun.start(
            PlanId.of(1),
            "迭代计划",
            PlanTrigger.SCHEDULED,
            List.of(
                    new PlanTaskSnapshot(TaskId.of(11), "接口"),
                    new PlanTaskSnapshot(TaskId.of(12), "页面")));

    @Test
    @DisplayName("全部任务成功时生成完整总结")
    void 全部任务成功时生成完整总结() {
        run.recordDispatched(TaskId.of(11));
        run.recordDispatched(TaskId.of(12));
        run.observe(TaskId.of(11), PlanTaskStatus.SUCCEEDED, "接口已完成");
        run.observe(TaskId.of(12), PlanTaskStatus.SUCCEEDED, "页面已完成");

        assertTrue(run.settleIfFinished());
        assertEquals(PlanRunStatus.COMPLETED, run.getStatus());
        assertTrue(run.getSummary().contains("成功 2"));
        assertTrue(run.getSummary().contains("#11 接口：成功（接口已完成）"));
    }

    @Test
    @DisplayName("部分成功时计划总结为 PARTIAL")
    void 部分成功时计划总结为PARTIAL() {
        run.recordDispatched(TaskId.of(11));
        run.recordDispatched(TaskId.of(12));
        run.observe(TaskId.of(11), PlanTaskStatus.SUCCEEDED, "完成");
        run.observe(TaskId.of(12), PlanTaskStatus.FAILED, "失败");

        assertTrue(run.settleIfFinished());
        assertEquals(PlanRunStatus.PARTIAL, run.getStatus());
        assertTrue(run.getSummary().contains("失败 1"));
    }

    @Test
    @DisplayName("全部跳过时计划总结为 FAILED")
    void 全部跳过时计划总结为FAILED() {
        run.recordDispatchFailure(TaskId.of(11), "任务已完成");
        run.recordDispatchFailure(TaskId.of(12), "项目已暂停");

        assertTrue(run.settleIfFinished());
        assertEquals(PlanRunStatus.FAILED, run.getStatus());
        assertTrue(run.getSummary().contains("跳过 2"));
    }

    @Test
    @DisplayName("任务等待决策时本轮保持运行")
    void 任务等待决策时本轮保持运行() {
        run.recordDispatched(TaskId.of(11));
        run.recordDispatched(TaskId.of(12));
        run.observe(TaskId.of(11), PlanTaskStatus.BLOCKED, "等待批准");
        run.observe(TaskId.of(12), PlanTaskStatus.SUCCEEDED, "完成");

        assertFalse(run.settleIfFinished());
        assertEquals(PlanRunStatus.RUNNING, run.getStatus());
        assertEquals(1, run.count(PlanTaskStatus.BLOCKED));
    }
}
