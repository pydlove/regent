package com.pydlove.regent.deployment.interfaces.rest;

import com.pydlove.regent.deployment.application.command.DeploymentCommandService;
import com.pydlove.regent.deployment.application.command.DeploymentExecutionService;
import com.pydlove.regent.deployment.application.command.DeploymentScriptGenerationService;
import com.pydlove.regent.deployment.application.command.GenerateDeploymentScriptCommand;
import com.pydlove.regent.deployment.application.command.SaveDeploymentCommand;
import com.pydlove.regent.deployment.application.query.DeploymentQueryService;
import com.pydlove.regent.deployment.application.query.DeploymentRunQueryService;
import com.pydlove.regent.deployment.application.query.DeploymentServerQueryService;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionMode;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.model.DeploymentReviewReference;
import com.pydlove.regent.deployment.domain.model.DeploymentServerId;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentDetailResponse;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentRunDetailResponse;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentRunRequest;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentRunSummaryResponse;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentSaveRequest;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentScriptIssueResponse;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentScriptGenerationRequest;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentScriptGenerationResponse;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentScriptValidationRequest;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentScriptValidationResponse;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentSummaryResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.application.query.ProjectAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.review.application.command.ReviewGateApproval;
import com.pydlove.regent.review.application.command.ReviewGateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 部署配置、执行与日志接口。 */
@RestController
@RequestMapping("/api")
public class DeploymentController {

    private final DeploymentCommandService commandService;
    private final DeploymentExecutionService executionService;
    private final DeploymentScriptGenerationService scriptGenerationService;
    private final DeploymentQueryService queryService;
    private final DeploymentRunQueryService runQueryService;
    private final DeploymentServerQueryService serverQueryService;
    private final ProjectAccessService projectAccessService;
    private final ReviewGateService reviewGateService;

    public DeploymentController(DeploymentCommandService commandService,
                                DeploymentExecutionService executionService,
                                DeploymentScriptGenerationService scriptGenerationService,
                                DeploymentQueryService queryService,
                                DeploymentRunQueryService runQueryService,
                                DeploymentServerQueryService serverQueryService,
                                ProjectAccessService projectAccessService,
                                ReviewGateService reviewGateService) {
        this.commandService = commandService;
        this.executionService = executionService;
        this.scriptGenerationService = scriptGenerationService;
        this.queryService = queryService;
        this.runQueryService = runQueryService;
        this.serverQueryService = serverQueryService;
        this.projectAccessService = projectAccessService;
        this.reviewGateService = reviewGateService;
    }

    @GetMapping("/deployments")
    public List<DeploymentSummaryResponse> list(
            @RequestParam(required = false) Long projectId,
            HttpServletRequest request) {
        ProjectId owner = projectId == null ? null : ProjectId.of(projectId);
        return queryService.list(owner, currentUser(request)).stream()
                .map(DeploymentSummaryResponse::from)
                .toList();
    }

    @GetMapping("/deployments/{id}")
    public DeploymentDetailResponse detail(@PathVariable long id, HttpServletRequest request) {
        return DeploymentDetailResponse.from(queryService.get(DeploymentId.of(id), currentUser(request)));
    }

    @PostMapping("/deployments")
    public DeploymentDetailResponse create(@RequestBody DeploymentSaveRequest request,
                                           HttpServletRequest servletRequest) {
        UserId ownerId = currentUser(servletRequest);
        requireProjectAndServer(request, ownerId);
        var created = commandService.create(toCommand(request));
        return DeploymentDetailResponse.from(queryService.get(created.getId(), ownerId));
    }

    @PostMapping("/deployments/script-validation")
    public DeploymentScriptValidationResponse validateScript(
            @RequestBody DeploymentScriptValidationRequest request) {
        var issues = commandService.validateScript(request.scriptContent()).stream()
                .map(DeploymentScriptIssueResponse::from)
                .toList();
        return new DeploymentScriptValidationResponse(issues.isEmpty(), issues);
    }

    @PostMapping("/deployments/script-generation")
    public DeploymentScriptGenerationResponse generateScript(
            @RequestBody DeploymentScriptGenerationRequest request,
            HttpServletRequest servletRequest) {
        if (request.projectId() == null) {
            throw new IllegalArgumentException("缺少 projectId");
        }
        if (request.serverId() == null) {
            throw new IllegalArgumentException("缺少 serverId");
        }
        UserId ownerId = currentUser(servletRequest);
        projectAccessService.require(ProjectId.of(request.projectId()), ownerId);
        requireServerOwned(request.serverId(), ownerId);
        var generated = scriptGenerationService.generate(new GenerateDeploymentScriptCommand(
                ProjectId.of(request.projectId()),
                DeploymentServerId.of(request.serverId()),
                request.workspacePath(),
                request.remoteDirectory()), request.requestId());
        return DeploymentScriptGenerationResponse.from(generated);
    }

    @PostMapping("/deployments/script-generation/{requestId}/cancel")
    public boolean cancelScriptGeneration(@PathVariable String requestId) {
        return scriptGenerationService.cancel(requestId);
    }

    @PutMapping("/deployments/{id}")
    public DeploymentDetailResponse update(@PathVariable long id,
                                           @RequestBody DeploymentSaveRequest request,
                                           HttpServletRequest servletRequest) {
        UserId ownerId = currentUser(servletRequest);
        queryService.get(DeploymentId.of(id), ownerId);
        requireProjectAndServer(request, ownerId);
        var updated = commandService.update(DeploymentId.of(id), toCommand(request));
        return DeploymentDetailResponse.from(queryService.get(updated.getId(), ownerId));
    }

    @DeleteMapping("/deployments/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
        queryService.get(DeploymentId.of(id), currentUser(request));
        commandService.delete(DeploymentId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deployments/{id}/run")
    public DeploymentRunSummaryResponse run(@PathVariable long id,
                                            @RequestBody(required = false) DeploymentRunRequest body,
                                            HttpServletRequest request) {
        UserId ownerId = currentUser(request);
        queryService.get(DeploymentId.of(id), ownerId);
        ProjectId projectId = body == null || body.projectId() == null ? null : ProjectId.of(body.projectId());
        DeploymentReviewReference reviewReference = null;
        if (body != null && body.taskId() != null) {
            ReviewGateApproval approval = reviewGateService.requireApproved(
                    TaskId.of(body.taskId()), ownerId);
            ProjectId taskProjectId = ProjectId.of(approval.projectId());
            if (projectId != null && !projectId.equals(taskProjectId)) {
                throw new IllegalArgumentException("部署项目与任务所属项目不一致");
            }
            projectId = taskProjectId;
            reviewReference = new DeploymentReviewReference(
                    approval.taskId(), approval.snapshotId(), approval.decisionId());
        }
        if (projectId != null) {
            projectAccessService.require(projectId, ownerId);
        }
        DeploymentRun run = executionService.start(DeploymentId.of(id), projectId, reviewReference);
        return DeploymentRunSummaryResponse.from(run);
    }

    @PostMapping("/deployments/{id}/stop")
    public DeploymentRunSummaryResponse stop(@PathVariable long id, HttpServletRequest request) {
        queryService.get(DeploymentId.of(id), currentUser(request));
        DeploymentRun run = executionService.stopActive(DeploymentId.of(id));
        return DeploymentRunSummaryResponse.from(run);
    }

    @GetMapping("/deployments/{id}/runs")
    public List<DeploymentRunSummaryResponse> runs(@PathVariable long id,
                                                   @RequestParam(defaultValue = "30") int limit,
                                                   HttpServletRequest request) {
        queryService.get(DeploymentId.of(id), currentUser(request));
        return runQueryService.listByDeployment(DeploymentId.of(id), limit).stream()
                .map(DeploymentRunSummaryResponse::from)
                .toList();
    }

    @GetMapping("/deployment-runs/{id}")
    public DeploymentRunDetailResponse runDetail(@PathVariable long id, HttpServletRequest request) {
        DeploymentRunId runId = DeploymentRunId.of(id);
        UserId ownerId = currentUser(request);
        DeploymentRun run = runQueryService.get(runId, ownerId);
        return DeploymentRunDetailResponse.from(run, runQueryService.log(runId));
    }

    private void requireProjectAndServer(DeploymentSaveRequest request, UserId ownerId) {
        java.util.List<Long> projectIds = request.projectIds() == null || request.projectIds().isEmpty()
                ? (request.projectId() == null ? java.util.List.of() : java.util.List.of(request.projectId()))
                : request.projectIds();
        if (projectIds.isEmpty()) {
            throw new IllegalArgumentException("缺少 projectIds");
        }
        if (request.serverId() == null) {
            throw new IllegalArgumentException("缺少 serverId");
        }
        for (Long projectId : projectIds) {
            if (projectId == null) {
                throw new IllegalArgumentException("projectIds 不能包含空值");
            }
            projectAccessService.require(ProjectId.of(projectId), ownerId);
        }
        requireServerOwned(request.serverId(), ownerId);
    }

    private void requireServerOwned(Long serverId, UserId ownerId) {
        if (serverId == null) {
            throw new IllegalArgumentException("缺少 serverId");
        }
        // 复用服务器查询的归属校验；详情内容不需要，只要确保它属于当前账号。
        serverQueryService.get(DeploymentServerId.of(serverId), ownerId);
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }

    private static SaveDeploymentCommand toCommand(DeploymentSaveRequest request) {
        java.util.List<Long> rawProjectIds = request.projectIds() == null || request.projectIds().isEmpty()
                ? (request.projectId() == null ? java.util.List.of() : java.util.List.of(request.projectId()))
                : request.projectIds();
        if (rawProjectIds.isEmpty()) throw new IllegalArgumentException("缺少 projectIds");
        if (request.serverId() == null) {
            throw new IllegalArgumentException("缺少 serverId");
        }
        return new SaveDeploymentCommand(
                rawProjectIds.stream().map(ProjectId::of).toList(),
                DeploymentServerId.of(request.serverId()),
                request.name(),
                request.remoteDirectory(),
                request.scriptFilename(),
                request.scriptContent(),
                DeploymentExecutionMode.parse(request.executionMode()),
                request.projectScriptPath());
    }
}
