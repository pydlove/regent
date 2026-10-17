package com.pydlove.regent.plan.application.command;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.support.ExecutionFakes;
import com.pydlove.regent.support.PlanFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanCommandServiceTest {

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final PlanFakes.Plans plans = new PlanFakes.Plans();
    private PlanCommandService service;

    @BeforeEach
    void setUp() {
        service = new PlanCommandService(plans, tasks);
        tasks.add(ExecutionFakes.task(1, ProjectId.of(1), com.pydlove.regent.execution.domain.model.TaskStatus.PENDING));
        tasks.add(ExecutionFakes.task(2, ProjectId.of(1), com.pydlove.regent.execution.domain.model.TaskStatus.PENDING));
    }

    @Test
    @DisplayName("创建计划前校验任务存在并落库")
    void 创建计划前校验任务存在并落库() {
        var plan = service.create(new CreatePlanCommand(
                "发布", List.of(TaskId.of(1), TaskId.of(2)), Instant.now().plusSeconds(60)));

        assertNotNull(plan.getId());
        assertEquals(plans.get(plan.getId().value()), plan);
    }

    @Test
    @DisplayName("纳管不存在的任务时报 404 语义异常")
    void 纳管不存在的任务时失败() {
        assertThrows(EntityNotFoundException.class, () -> service.create(
                new CreatePlanCommand("坏计划", List.of(TaskId.of(99)), Instant.now())));
    }
}
