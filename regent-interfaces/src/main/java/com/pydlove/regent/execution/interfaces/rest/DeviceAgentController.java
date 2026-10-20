package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.AgentRunService;
import com.pydlove.regent.execution.application.query.AgentContractService;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.execution.interfaces.dto.AgentClaimRequest;
import com.pydlove.regent.execution.interfaces.dto.AgentContractResponse;
import com.pydlove.regent.execution.interfaces.dto.AgentHeartbeatRequest;
import com.pydlove.regent.execution.interfaces.dto.AgentReportRequest;
import com.pydlove.regent.execution.interfaces.dto.AgentReportResponse;
import com.pydlove.regent.execution.interfaces.dto.AgentRunSnapshotResponse;
import com.pydlove.regent.execution.interfaces.dto.AgentTaskClaimResponse;
import com.pydlove.regent.execution.interfaces.dto.AgentTaskSummaryResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前设备代理使用的任务协议。
 *
 * <p>设备先用已登录页面换取 Bearer 会话，再凭令牌访问这里。与 /api/agent 的本机协议相比，
 * 这里的每个动作都额外校验任务或项目归属于令牌对应账号，公网中继也可以安全转发。
 */
@RestController
@RequestMapping("/api/device-agent")
public class DeviceAgentController {

    private final AgentRunService agentRunService;
    private final AgentContractService agentContractService;
    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final ProjectProfileRepository projectProfileRepository;

    public DeviceAgentController(AgentRunService agentRunService,
                                 AgentContractService agentContractService,
                                 TaskRepository taskRepository,
                                 TaskRunRepository taskRunRepository,
                                 ProjectProfileRepository projectProfileRepository) {
        this.agentRunService = agentRunService;
        this.agentContractService = agentContractService;
        this.taskRepository = taskRepository;
        this.taskRunRepository = taskRunRepository;
        this.projectProfileRepository = projectProfileRepository;
    }

    @GetMapping("/contract")
    public AgentContractResponse contract() {
        return AgentContractResponse.from(agentContractService.current());
    }

    @GetMapping(value = "/contract.md", produces = "text/markdown;charset=UTF-8")
    public String contractMarkdown() {
        return agentContractService.current().markdown();
    }

    @GetMapping("/tasks")
    public List<AgentTaskSummaryResponse> tasks(@RequestParam String project,
                                                @RequestParam(required = false) Integer limit,
                                                HttpServletRequest request) {
        UserId owner = currentUser(request);
        requireProjectOwned(project, owner);
        return agentRunService.list(project, limit == null ? 20 : Math.max(1, Math.min(limit, 100)))
                .stream()
                .map(AgentTaskSummaryResponse::from)
                .toList();
    }

    @PostMapping("/tasks/claim")
    public ResponseEntity<AgentTaskClaimResponse> claim(@RequestBody AgentClaimRequest claim,
                                                        HttpServletRequest request) {
        UserId owner = currentUser(request);
        if (claim.taskId() != null) {
            requireTaskOwned(TaskId.of(claim.taskId()), owner);
        } else {
            requireProjectOwned(claim.projectCode(), owner);
        }
        return agentRunService.claim(
                        claim.executor(), claim.sessionId(), claim.projectCode(), claim.taskId())
                .map(AgentTaskClaimResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/runs/{runId}/heartbeat")
    public AgentRunSnapshotResponse heartbeat(@PathVariable long runId,
                                              @RequestBody AgentHeartbeatRequest heartbeat,
                                              HttpServletRequest request) {
        requireRunOwned(runId, currentUser(request));
        return AgentRunSnapshotResponse.from(
                agentRunService.heartbeat(runId, heartbeat.executor()));
    }

    @PostMapping("/runs/{runId}/report")
    public AgentReportResponse report(@PathVariable long runId,
                                      @RequestBody AgentReportRequest report,
                                      HttpServletRequest request) {
        requireRunOwned(runId, currentUser(request));
        return AgentReportResponse.from(agentRunService.report(
                runId, report.executor(), report.toReport(),
                report.sessionId(), report.toTokens(), report.rawResult()));
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }

    private void requireProjectOwned(String projectCode, UserId owner) {
        if (projectCode == null || projectCode.isBlank()) {
            throw new IllegalArgumentException("设备代理必须指定项目标识");
        }
        ProjectProfile project = projectProfileRepository.findByCode(projectCode)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectCode));
        if (project.ownerId() == null || !project.ownerId().equals(owner)) {
            throw new EntityNotFoundException("项目不存在：" + projectCode);
        }
    }

    private void requireTaskOwned(TaskId taskId, UserId owner) {
        taskRepository.findByIdOwned(taskId, owner)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId.value()));
    }

    private void requireRunOwned(long runId, UserId owner) {
        var run = taskRunRepository.findById(TaskRunId.of(runId))
                .orElseThrow(() -> new EntityNotFoundException("执行记录不存在：" + runId));
        requireTaskOwned(run.getTaskId(), owner);
    }
}
