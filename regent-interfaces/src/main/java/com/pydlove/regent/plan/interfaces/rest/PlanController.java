package com.pydlove.regent.plan.interfaces.rest;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.plan.application.command.CreatePlanCommand;
import com.pydlove.regent.plan.application.command.PlanCommandService;
import com.pydlove.regent.plan.application.command.PlanExecutionService;
import com.pydlove.regent.plan.application.query.PlanQueryService;
import com.pydlove.regent.plan.domain.model.Plan;
import com.pydlove.regent.plan.domain.model.PlanId;
import com.pydlove.regent.plan.domain.model.PlanRun;
import com.pydlove.regent.plan.domain.model.PlanRunId;
import com.pydlove.regent.plan.interfaces.dto.PlanCreateRequest;
import com.pydlove.regent.plan.interfaces.dto.PlanDetailResponse;
import com.pydlove.regent.plan.interfaces.dto.PlanRunDetailResponse;
import com.pydlove.regent.plan.interfaces.dto.PlanRunResponse;
import com.pydlove.regent.plan.interfaces.dto.PlanSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** 计划管理与执行接口。 */
@RestController
@RequestMapping("/api")
public class PlanController {

    private final PlanCommandService planCommandService;
    private final PlanExecutionService planExecutionService;
    private final PlanQueryService planQueryService;

    public PlanController(PlanCommandService planCommandService,
                          PlanExecutionService planExecutionService,
                          PlanQueryService planQueryService) {
        this.planCommandService = planCommandService;
        this.planExecutionService = planExecutionService;
        this.planQueryService = planQueryService;
    }

    @GetMapping("/plans")
    public List<PlanSummaryResponse> list() {
        return planQueryService.list().stream().map(PlanSummaryResponse::from).toList();
    }

    @GetMapping("/plans/{id}")
    public PlanDetailResponse detail(@PathVariable long id) {
        return PlanDetailResponse.from(planQueryService.get(PlanId.of(id)));
    }

    @PostMapping("/plans")
    public PlanSummaryResponse create(@RequestBody PlanCreateRequest request) {
        if (request.taskIds() == null || request.taskIds().isEmpty()) {
            throw new IllegalArgumentException("至少选择一个任务");
        }
        List<TaskId> taskIds = request.taskIds().stream().map(taskId -> {
            if (taskId == null) {
                throw new IllegalArgumentException("任务 ID 不能为空");
            }
            return TaskId.of(taskId);
        }).toList();
        Plan plan = planCommandService.create(new CreatePlanCommand(
                request.name(),
                taskIds,
                request.scheduledAt() == null ? Instant.now() : request.scheduledAt()));
        return PlanSummaryResponse.from(planQueryService.get(plan.getId()).plan());
    }

    @PostMapping("/plans/{id}/run")
    public PlanRunResponse run(@PathVariable long id) {
        PlanRun run = planExecutionService.runNow(PlanId.of(id));
        return PlanRunResponse.from(planQueryService.getRun(run.getId()).run());
    }

    @GetMapping("/plan-runs/{id}")
    public PlanRunDetailResponse runDetail(@PathVariable long id) {
        return PlanRunDetailResponse.from(planQueryService.getRun(PlanRunId.of(id)));
    }
}
