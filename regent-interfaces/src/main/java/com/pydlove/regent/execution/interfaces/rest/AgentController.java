package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.AgentRunService;
import com.pydlove.regent.execution.application.query.AgentContractService;
import com.pydlove.regent.execution.interfaces.dto.AgentClaimRequest;
import com.pydlove.regent.execution.interfaces.dto.AgentContractResponse;
import com.pydlove.regent.execution.interfaces.dto.AgentHeartbeatRequest;
import com.pydlove.regent.execution.interfaces.dto.AgentReportRequest;
import com.pydlove.regent.execution.interfaces.dto.AgentReportResponse;
import com.pydlove.regent.execution.interfaces.dto.AgentRunSnapshotResponse;
import com.pydlove.regent.execution.interfaces.dto.AgentTaskClaimResponse;
import com.pydlove.regent.execution.interfaces.dto.AgentTaskSummaryResponse;
import org.springframework.http.MediaType;
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
 * 外部执行者接口 —— 让 codex 桌面版、别的 CLI、别的模型工具连上来干活。
 *
 * <p>与本项目其它接口的分工不同：那些接口服务的是人（界面），这个服务的是**程序**。
 * 因此它多了一条别人没有的要求：{@code GET /api/agent/contract} 必须在一次请求里
 * 说清「怎么认领、怎么上报、等批复时干什么」，让一个从没读过本项目代码的工具也能开工 ——
 * 协议全文来自 classpath，与平台自己拉起的执行器读到的是同一份。
 *
 * <p>这一层只做三件事：把请求转成应用服务的入参、调它、把结果转成响应 DTO。
 * 「没有活」是 204 而不是 404：对轮询的调用方来说，「现在没活」是一个正常答案。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentRunService agentRunService;
    private final AgentContractService agentContractService;

    public AgentController(AgentRunService agentRunService, AgentContractService agentContractService) {
        this.agentRunService = agentRunService;
        this.agentContractService = agentContractService;
    }

    @GetMapping("/contract")
    public AgentContractResponse contract() {
        return AgentContractResponse.from(agentContractService.current());
    }

    /** 纯文本版：工具可以直接把它塞进上下文，不需要先解析 JSON。 */
    @GetMapping(value = "/contract.md", produces = "text/markdown;charset=UTF-8")
    public String contractMarkdown() {
        return agentContractService.current().markdown();
    }

    /**
     * 可领任务清单。先看再领 —— 人也是这么用的：「这个项目上有哪些活？」
     *
     * <p>{@code project} 按项目标识过滤；不传就是全部。默认只回 20 条：执行者一次干一条，
     * 列一屏它挑得动的就够，把整个积压倒给它除了浪费上下文没有任何用。
     */
    @GetMapping("/tasks")
    public List<AgentTaskSummaryResponse> tasks(@RequestParam(required = false) String project,
                                                @RequestParam(required = false) Integer limit) {
        return agentRunService.list(project, limit == null ? 20 : Math.max(1, Math.min(limit, 100))).stream()
                .map(AgentTaskSummaryResponse::from)
                .toList();
    }

    @PostMapping("/tasks/claim")
    public ResponseEntity<AgentTaskClaimResponse> claim(@RequestBody AgentClaimRequest request) {
        return agentRunService.claim(request.executor(), request.sessionId(), request.projectCode(), request.taskId())
                .map(AgentTaskClaimResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/runs/{runId}/heartbeat")
    public AgentRunSnapshotResponse heartbeat(@PathVariable long runId,
                                              @RequestBody AgentHeartbeatRequest request) {
        return AgentRunSnapshotResponse.from(agentRunService.heartbeat(runId, request.executor()));
    }

    @PostMapping("/runs/{runId}/report")
    public AgentReportResponse report(@PathVariable long runId,
                                      @RequestBody AgentReportRequest request) {
        return AgentReportResponse.from(agentRunService.report(runId, request.executor(),
                request.toReport(), request.sessionId(), request.toTokens(), request.rawResult()));
    }
}
