package com.pydlove.regent.deployment.interfaces.rest;

import com.pydlove.regent.deployment.application.command.DeploymentCommandService;
import com.pydlove.regent.deployment.application.command.DeploymentExecutionService;
import com.pydlove.regent.deployment.application.command.SaveDeploymentCommand;
import com.pydlove.regent.deployment.application.query.DeploymentQueryService;
import com.pydlove.regent.deployment.application.query.DeploymentRunQueryService;
import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentId;
import com.pydlove.regent.deployment.domain.model.DeploymentRun;
import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentDetailResponse;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentRunDetailResponse;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentRunSummaryResponse;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentSaveRequest;
import com.pydlove.regent.deployment.interfaces.dto.DeploymentSummaryResponse;
import com.pydlove.regent.project.domain.model.ProjectId;
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
    private final DeploymentQueryService queryService;
    private final DeploymentRunQueryService runQueryService;

    public DeploymentController(DeploymentCommandService commandService,
                                DeploymentExecutionService executionService,
                                DeploymentQueryService queryService,
                                DeploymentRunQueryService runQueryService) {
        this.commandService = commandService;
        this.executionService = executionService;
        this.queryService = queryService;
        this.runQueryService = runQueryService;
    }

    @GetMapping("/deployments")
    public List<DeploymentSummaryResponse> list(
            @RequestParam(required = false) Long projectId) {
        ProjectId owner = projectId == null ? null : ProjectId.of(projectId);
        return queryService.list(owner).stream().map(DeploymentSummaryResponse::from).toList();
    }

    @GetMapping("/deployments/{id}")
    public DeploymentDetailResponse detail(@PathVariable long id) {
        return DeploymentDetailResponse.from(queryService.get(DeploymentId.of(id)));
    }

    @PostMapping("/deployments")
    public DeploymentDetailResponse create(@RequestBody DeploymentSaveRequest request) {
        var created = commandService.create(toCommand(request));
        return DeploymentDetailResponse.from(queryService.get(created.getId()));
    }

    @PutMapping("/deployments/{id}")
    public DeploymentDetailResponse update(@PathVariable long id,
                                           @RequestBody DeploymentSaveRequest request) {
        var updated = commandService.update(DeploymentId.of(id), toCommand(request));
        return DeploymentDetailResponse.from(queryService.get(updated.getId()));
    }

    @DeleteMapping("/deployments/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        commandService.delete(DeploymentId.of(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/deployments/{id}/run")
    public DeploymentRunSummaryResponse run(@PathVariable long id) {
        DeploymentRun run = executionService.start(DeploymentId.of(id));
        return DeploymentRunSummaryResponse.from(run);
    }

    @GetMapping("/deployments/{id}/runs")
    public List<DeploymentRunSummaryResponse> runs(@PathVariable long id,
                                                   @RequestParam(defaultValue = "30") int limit) {
        return runQueryService.listByDeployment(DeploymentId.of(id), limit).stream()
                .map(DeploymentRunSummaryResponse::from)
                .toList();
    }

    @GetMapping("/deployment-runs/{id}")
    public DeploymentRunDetailResponse runDetail(@PathVariable long id) {
        DeploymentRunId runId = DeploymentRunId.of(id);
        DeploymentRun run = runQueryService.get(runId);
        return DeploymentRunDetailResponse.from(run, runQueryService.log(runId));
    }

    private static SaveDeploymentCommand toCommand(DeploymentSaveRequest request) {
        if (request.port() == null) {
            throw new IllegalArgumentException("缺少 port");
        }
        if (request.projectId() == null) {
            throw new IllegalArgumentException("缺少 projectId");
        }
        return new SaveDeploymentCommand(
                ProjectId.of(request.projectId()),
                request.name(),
                request.host(),
                request.port(),
                request.username(),
                DeploymentAuthType.parse(request.authType()),
                request.remoteDirectory(),
                request.scriptFilename(),
                request.scriptContent(),
                request.password());
    }
}
