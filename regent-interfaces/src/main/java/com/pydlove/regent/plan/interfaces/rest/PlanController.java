package com.pydlove.regent.plan.interfaces.rest;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
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
import jakarta.servlet.http.HttpServletRequest;
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
    public List<PlanSummaryResponse> list(HttpServletRequest request) {
        return planQueryService.list(currentUser(request)).stream()
                .map(PlanSummaryResponse::from)
                .toList();
    }

    @GetMapping("/plans/{id}")
    public PlanDetailResponse detail(@PathVariable long id, HttpServletRequest request) {
        return PlanDetailResponse.from(planQueryService.get(PlanId.of(id), currentUser(request)));
    }

    @PostMapping("/plans")
    public PlanSummaryResponse create(@RequestBody PlanCreateRequest request,
                                      HttpServletRequest servletRequest) {
        if (request.taskIds() == null || request.taskIds().isEmpty()) {
            throw new IllegalArgumentException("至少选择一个任务");
        }
        List<TaskId> taskIds = request.taskIds().stream().map(taskId -> {
            if (taskId == null) {
                throw new IllegalArgumentException("任务 ID 不能为空");
            }
            return TaskId.of(taskId);
        }).toList();
        UserId ownerId = currentUser(servletRequest);
        Plan plan = planCommandService.create(new CreatePlanCommand(
                request.name(),
                taskIds,
                request.scheduledAt() == null ? Instant.now() : request.scheduledAt()), ownerId);
        return PlanSummaryResponse.from(planQueryService.get(plan.getId(), ownerId).plan());
    }

    @PostMapping("/plans/{id}/run")
    public PlanRunResponse run(@PathVariable long id, HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        planQueryService.get(PlanId.of(id), ownerId);
        PlanRun run = planExecutionService.runNow(PlanId.of(id));
        return PlanRunResponse.from(planQueryService.getRun(run.getId(), ownerId).run());
    }

    @GetMapping("/plan-runs/{id}")
    public PlanRunDetailResponse runDetail(@PathVariable long id, HttpServletRequest request) {
        return PlanRunDetailResponse.from(
                planQueryService.getRun(PlanRunId.of(id), currentUser(request)));
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }
}
