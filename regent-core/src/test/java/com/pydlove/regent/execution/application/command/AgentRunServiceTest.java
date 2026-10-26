package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.ExecReport;
import com.pydlove.regent.execution.domain.model.ExecutionDevice;
import com.pydlove.regent.execution.domain.model.ReportedBug;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskDocument;
import com.pydlove.regent.execution.domain.model.TaskAdjustment;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentStatus;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.port.ExecutorContract;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.service.ExecutionConcurrencyGate;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.shared.kernel.TokenUsage;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外部执行者这条路的守卫。
 *
 * <p>它盯的是三类事：谁被允许领走一条任务（全局并发、状态可领）、
 * 决策往来是否与内部执行者一字不差（阈值内放行、超阈值挂起、不可逆必须问人），
 * 以及执行者消失之后平台收不收得回来（租约到期判失联）。
 */
class AgentRunServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final String AGENT = "codex-desktop@test";

    @TempDir
    Path tempDir;

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Events events = new ExecutionFakes.Events();
    private final ExecutionFakes.Adjustments adjustments = new ExecutionFakes.Adjustments();
    private final ExecutionFakes.Decisions decisions = new ExecutionFakes.Decisions();
    private final ExecutionFakes.Bugs bugs = new ExecutionFakes.Bugs();
    private final ExecutionFakes.Rules rules = new ExecutionFakes.Rules();
    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final ExecutionFakes.Attachments attachments = new ExecutionFakes.Attachments();
    private final ExecutionFakes.Documents documents = new ExecutionFakes.Documents();
    private final ExecutionFakes.Environments environments = new ExecutionFakes.Environments();
    private final ExecutionFakes.TaskPlugins plugins = new ExecutionFakes.TaskPlugins();
    private final ExecutionFakes.Branches branches = new ExecutionFakes.Branches();
    private final ExecutionFakes.Devices devices = new ExecutionFakes.Devices();
    private final ExecutionFakes.Transactions transactions = new ExecutionFakes.Transactions();
    private final ExecutionConcurrencyGate concurrencyGate = new ExecutionConcurrencyGate(64);

    private AgentRunService service;

    @BeforeEach
    void setUp() {
        tasks.wire(runs);
        events.wire(runs);
        projects.put(projectProfile(1L));
        service = serviceWithLease(Duration.ofMinutes(15));
    }

    private AgentRunService serviceWithLease(Duration lease) {
        DecisionRaiseService decisionRaiseService = new DecisionRaiseService(rules, decisions);
        DeviceRestartRecoveryService restartRecoveryService =
                new DeviceRestartRecoveryService(tasks, runs, transactions);
        return new AgentRunService(tasks, runs, devices, events, attachments, documents,
                projects, environments, plugins,
                decisions, branches, new BranchProvisioningService(branches),
                new RunSettlementService(tasks, runs, adjustments, decisions, decisionRaiseService, bugs,
                        taskId -> {
                        }, new ExecutionFakes.Transactions()),
                contract(), concurrencyGate, transactions, restartRecoveryService, lease,
                "http://127.0.0.1:8787/api/agent/contract");
    }

    private static ExecutorContract contract() {
        return new ExecutorContract() {
            @Override
            public String reportingRules() {
                return "上报约定：三种结局选一个";
            }

            @Override
            public String agentProtocol() {
                return "协议：先认领，再干活";
            }

            @Override
            public String resultSchemaJson() {
                return "{}";
            }
        };
    }

    @Test
    @DisplayName("认领：任务进入执行中，工作包带齐分支、指令与租约")
    void 认领拿到工作包() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));

        AgentWorkPackage work = service.claim(AGENT, "sess-1", "p1", null).orElseThrow();

        assertEquals(7L, work.taskId());
        assertEquals(1, work.attemptNo());
        assertEquals(TaskStatus.RUNNING.name(), tasks.get(7L).getStatus().name());
        assertEquals(projectPath(1L).toString(), work.workdir());
        assertEquals("main", work.branch());
        assertTrue(work.instruction().startsWith("把这件事做完"), work.instruction());
        assertTrue(work.instruction().contains("上报约定"), "指令末尾必须带上上报约定");
        assertEquals(900, work.leaseSeconds());
        assertNotNull(work.leaseExpiresAt());
        assertEquals("L1", work.decisionThreshold());
        assertEquals(RunStatus.RUNNING.name(), runs.get(work.runId()).getStatus().name());
        assertEquals(AGENT, runs.get(work.runId()).getAgentName());
        assertFalse(work.images().stream().findAny().isPresent());
        assertTrue(work.plugins().isEmpty());
    }

    @Test
    @DisplayName("认领者可以批量追加实时执行事件")
    void 追加实时执行事件() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        AgentWorkPackage work = service.claim(AGENT, "sess-live", "p1", null).orElseThrow();
        TaskRunId runId = TaskRunId.of(work.runId());
        int before = events.findByRun(runId).size();

        int appended = service.appendEvents(work.runId(), AGENT, List.of(
                new AgentRunEvent("thread.started", "{\"type\":\"thread.started\"}", null),
                new AgentRunEvent("item.completed", "{\"type\":\"item.completed\"}", null)));

        assertEquals(2, appended);
        assertEquals(before + 2, events.findByRun(runId).size());
        assertEquals(before, events.findByRun(runId).get(before).seq());
        assertThrows(RuntimeException.class, () -> service.appendEvents(
                work.runId(), "other-agent", List.of(
                        new AgentRunEvent("raw", "\"line\"", null))));
    }

    @Test
    @DisplayName("设备暂无法执行时任务进入排队而不是停留在待执行")
    void 设备等待执行位时任务排队() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));

        Task queued = service.queueForDevice(7L);

        assertEquals(TaskStatus.QUEUED, queued.getStatus());
        assertEquals(TaskStatus.QUEUED, tasks.get(7L).getStatus());
    }

    @Test
    @DisplayName("认领：工作包带有效环境，项目级同键覆盖全局")
    void 认领拿到有效环境() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        environments.add(EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "DB_HOST", "global-db", List.of("数据库"), "全局"));
        environments.add(EnvironmentEntry.create(
                EnvironmentScope.PROJECT, PROJECT, "DB_HOST", "project-db", List.of(), "项目"));
        environments.add(EnvironmentEntry.create(
                EnvironmentScope.PROJECT, PROJECT, "DB_PASSWORD", "secret", List.of(), "数据库密码", true));

        AgentWorkPackage work = service.claim(AGENT, "sess-1", "p1", null).orElseThrow();

        assertEquals(2, work.environment().size());
        AgentWorkPackage.EnvironmentVariable db = work.environment().stream()
                .filter(variable -> variable.key().equals("DB_HOST"))
                .findFirst()
                .orElseThrow();
        assertEquals("project-db", db.value());
        assertEquals("PROJECT", db.scope());
        assertFalse(work.instruction().contains("project-db"), work.instruction());
        assertFalse(work.instruction().contains("secret"), work.instruction());
    }

    @Test
    @DisplayName("外部执行者工作包同时拿到插件元数据和内容")
    void 认领拿到插件() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        plugins.add(TaskId.of(7L), ExecutionFakes.TaskPlugins.plugin(
                9L, "Kafka 读取组件", 2, "class KafkaReader { void read() {} }"));

        AgentWorkPackage work = service.claim(AGENT, "sess-1", "p1", null).orElseThrow();

        assertEquals(1, work.plugins().size());
        AgentWorkPackage.Plugin plugin = work.plugins().get(0);
        assertEquals(9L, plugin.id());
        assertEquals(2, plugin.revision());
        assertTrue(plugin.content().contains("KafkaReader"));
        assertTrue(work.instruction().contains("不要重新生成已有实现"));
    }

    @Test
    @DisplayName("外部执行者工作包同时拿到任务文档")
    void 认领拿到任务文档() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        documents.save(TaskDocument.of(
                TaskId.of(7L), null, "接口说明.pdf", "application/pdf",
                128, "/tmp/docs/interface.pdf"));

        AgentWorkPackage work = service.claim(AGENT, "sess-doc", "p1", null).orElseThrow();

        assertEquals(1, work.documents().size());
        assertEquals("接口说明.pdf", work.documents().get(0).filename());
        assertTrue(work.instruction().contains("## 任务文档"), work.instruction());
        assertTrue(work.instruction().contains("/tmp/docs/interface.pdf"), work.instruction());
    }

    @Test
    @DisplayName("认领：排队中的任务取得执行位后进入执行中")
    void 认领排队任务() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.QUEUED));

        AgentWorkPackage work = service.claim(AGENT, "sess-1", "p1", null).orElseThrow();

        assertEquals(7L, work.taskId());
        assertEquals(TaskStatus.RUNNING.name(), tasks.get(7L).getStatus().name());
    }

    @Test
    @DisplayName("默认在当前分支认领，不预建任务分支")
    void 默认使用当前分支() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));

        AgentWorkPackage work = service.claim(AGENT, "sess-1", "p1", null).orElseThrow();

        assertEquals(7L, work.taskId());
        assertEquals("main", work.branch());
        assertTrue(branches.created.isEmpty());
    }

    @Test
    @DisplayName("非 Git 目录也能认领，工作包与执行记录都不带分支")
    void 非Git目录认领不带分支() {
        branches.current = null;
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));

        AgentWorkPackage work = service.claim(AGENT, "sess-1", "p1", null).orElseThrow();

        assertNull(work.branch());
        assertTrue(tasks.get(7L).getBranchName().isEmpty());
        assertNull(runs.get(work.runId()).getBranchName());
        assertEquals(TaskStatus.RUNNING.name(), tasks.get(7L).getStatus().name());
    }

    @Test
    @DisplayName("没活时返回空：空不是错误，是「现在没活」这句实话")
    void 没活返回空() {
        assertTrue(service.claim(AGENT, "sess-1", null, null).isEmpty());
    }

    @Test
    @DisplayName("按项目找活：人给的是项目标识，执行者只该在那个项目里挑")
    void 按项目找活() {
        projects.put(projectProfile(2L));
        tasks.add(ExecutionFakes.task(1L, PROJECT, TaskStatus.PENDING));
        tasks.add(ExecutionFakes.task(2L, ProjectId.of(2L), TaskStatus.PENDING));

        AgentWorkPackage work = service.claim(AGENT, "sess-1", "p2", null).orElseThrow();

        assertEquals(2L, work.taskId());
        assertEquals(1L, service.list("p1", 20).get(0).taskId());
        assertEquals(0, service.list("p2", 20).size(), "领走之后它就不再是可领候选");
    }

    @Test
    @DisplayName("只受全局并发限制：同一个项目允许多个执行者同时干活")
    void 同项目可以并发执行() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        tasks.add(ExecutionFakes.task(8L, PROJECT, TaskStatus.PENDING));
        AgentWorkPackage first = service.claim(AGENT, "sess-1", "p1", null).orElseThrow();
        AgentWorkPackage second = service.claim("another-agent", "sess-2", "p1", null).orElseThrow();

        assertEquals(TaskStatus.RUNNING, tasks.get(7L).getStatus());
        assertEquals(TaskStatus.RUNNING, tasks.get(8L).getStatus());
        assertEquals(first.workdir(), second.workdir());
        assertTrue(service.list("p1", 20).isEmpty());
    }

    @Test
    @DisplayName("平台并发上限：其他项目也要排队，任务收场后立即放行")
    void 平台并发上限() {
        concurrencyGate.updateLimit(1);
        projects.put(projectProfile(2L));
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        tasks.add(ExecutionFakes.task(8L, ProjectId.of(2L), TaskStatus.PENDING));

        AgentWorkPackage first = service.claim(AGENT, "sess-1", null, null).orElseThrow();

        assertTrue(service.claim("another-agent", "sess-2", null, null).isEmpty());
        assertEquals(1L, service.list(null, 20).stream()
                .filter(summary -> !summary.claimable())
                .filter(summary -> summary.reason().contains("上限"))
                .count(), "第一条已被领走，清单里只剩第二条，应如实标成平台已满");

        service.report(first.runId(), AGENT,
                ExecReport.of("DONE", "做完了", List.of(), null, List.of()),
                "sess-1", TokenUsage.ZERO, null);

        AgentWorkPackage second = service.claim("another-agent", "sess-2", null, null).orElseThrow();
        assertEquals(8L, second.taskId());
    }

    @Test
    @DisplayName("平台已满时，已终止任务立即返回不可认领，不能进入排队重试")
    void 已终止任务不会被误认为容量不足() {
        concurrencyGate.updateLimit(1);
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        service.claim(AGENT, "sess-1", null, null).orElseThrow();

        Task cancelled = ExecutionFakes.task(8L, PROJECT, TaskStatus.PENDING);
        tasks.add(cancelled);
        cancelled.dispatch("codex/task-8");
        cancelled.cancel();

        DomainException error = assertThrows(DomainException.class,
                () -> service.claim("another-agent", "sess-2", null, 8L));

        assertTrue(error.getMessage().contains("状态：CANCELLED"), error.getMessage());
        assertFalse(error.getMessage().contains("上限"), error.getMessage());
    }

    @Test
    @DisplayName("同项目已有任务运行时，剩余任务仍可认领")
    void 同项目剩余任务仍可认领() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        tasks.add(ExecutionFakes.task(8L, PROJECT, TaskStatus.PENDING));
        service.claim(AGENT, "sess-1", null, null).orElseThrow();

        List<?> listed = service.list("p1", 20);
        assertEquals(1, listed.size());
        assertTrue(((com.pydlove.regent.execution.application.query.AgentTaskSummary) listed.get(0)).claimable());
    }

    @Test
    @DisplayName("阈值内的决策当场放行：同一次尝试里带着结论接着干")
    void 阈值内当场放行() {
        AgentWorkPackage work = claimed();

        AgentReportOutcome outcome = service.report(work.runId(), AGENT,
                report("BLOCKED", decision("L0", true)), "sess-1", TokenUsage.ZERO, null);

        assertEquals(AgentReportOutcome.Outcome.CONTINUE, outcome.outcome());
        assertTrue(outcome.instruction().contains("决策已归位"), outcome.instruction());
        assertEquals(RunStatus.RUNNING.name(), runs.get(work.runId()).getStatus().name());
        assertEquals(TaskStatus.RUNNING, tasks.get(work.taskId()).getStatus());
        assertEquals(ServiceRounds.LEFT_AFTER_FIRST, outcome.roundsLeft());
    }

    @Test
    @DisplayName("超阈值的决策挂起等人：这次尝试到此为止，任务跟着阻塞")
    void 超阈值挂起() {
        AgentWorkPackage work = claimed();

        AgentReportOutcome outcome = service.report(work.runId(), AGENT,
                report("BLOCKED", decision("L3", true)), "sess-1", TokenUsage.ZERO, null);

        assertEquals(AgentReportOutcome.Outcome.BLOCKED, outcome.outcome());
        assertNotNull(outcome.decisionId());
        assertEquals(RunStatus.BLOCKED.name(), runs.get(work.runId()).getStatus().name());
        assertEquals(TaskStatus.BLOCKED, tasks.get(work.taskId()).getStatus());
    }

    @Test
    @DisplayName("不可逆的改动一定问人：哪怕自评等级在阈值内")
    void 不可逆必须问人() {
        AgentWorkPackage work = claimed();

        AgentReportOutcome outcome = service.report(work.runId(), AGENT,
                report("BLOCKED", decision("L0", false)), "sess-1", TokenUsage.ZERO, null);

        assertEquals(AgentReportOutcome.Outcome.BLOCKED, outcome.outcome());
    }

    @Test
    @DisplayName("报 DONE 收敛任务：改动留在分支上等验收")
    void 报完成收敛() {
        AgentWorkPackage work = claimed();

        AgentReportOutcome outcome = service.report(work.runId(), AGENT,
                ExecReport.of("DONE", "改完了", List.of("src/a.ts"), null, List.of()),
                "sess-1", TokenUsage.ZERO, null);

        assertEquals(AgentReportOutcome.Outcome.DONE, outcome.outcome());
        assertEquals(TaskStatus.DONE, tasks.get(work.taskId()).getStatus());
        assertEquals(RunStatus.SUCCESS, runs.get(work.runId()).getStatus());
        assertNull(runs.get(work.runId()).getLeaseExpiresAt(), "收场即解除占用");
    }

    @Test
    @DisplayName("认领时带出待调整；上报后才标记已下发")
    void 外部执行者认领带出调整() {
        Task task = ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING);
        tasks.add(task);
        TaskAdjustment adjustment = adjustments.save(
                TaskAdjustment.temporary(task.getId(), "把错误提示也改清楚"));

        AgentWorkPackage work = service.claim(AGENT, "sess-1", null, null).orElseThrow();

        assertTrue(work.instruction().contains("把错误提示也改清楚"), work.instruction());
        assertEquals(TaskAdjustmentStatus.PENDING, adjustment.getStatus(),
                "工作包刚发出还不能确认执行者收到");

        service.report(work.runId(), AGENT,
                ExecReport.of("DONE", "调整完了", List.of(), null, List.of()),
                "sess-1", TokenUsage.ZERO, null);

        assertEquals(TaskAdjustmentStatus.APPLIED, adjustments.all().get(0).getStatus());
        assertEquals(work.runId(), adjustments.all().get(0).getAppliedRunId().orElseThrow().value());
    }

    @Test
    @DisplayName("执行期间新增调整：本次上报先不收场，下一次上报前必须继续")
    void 外部执行者执行期间新增调整() {
        AgentWorkPackage work = claimed();
        adjustments.save(TaskAdjustment.temporary(TaskId.of(work.taskId()), "再补一套接口测试"));

        AgentReportOutcome first = service.report(work.runId(), AGENT,
                ExecReport.of("DONE", "第一轮做完了", List.of(), null, List.of()),
                "sess-1", TokenUsage.ZERO, null);

        assertEquals(AgentReportOutcome.Outcome.CONTINUE, first.outcome());
        assertTrue(first.instruction().contains("再补一套接口测试"), first.instruction());
        assertEquals(TaskStatus.RUNNING, tasks.get(work.taskId()).getStatus());
        assertEquals(RunStatus.RUNNING, runs.get(work.runId()).getStatus());

        AgentReportOutcome second = service.report(work.runId(), AGENT,
                ExecReport.of("DONE", "调整也做完了", List.of(), null, List.of()),
                "sess-1", TokenUsage.ZERO, null);

        assertEquals(AgentReportOutcome.Outcome.DONE, second.outcome());
        assertEquals(TaskAdjustmentStatus.APPLIED, adjustments.all().get(0).getStatus());
    }

    @Test
    @DisplayName("上报发现的缺陷：任务照常完成，缺陷另存为 AI 来源的 Bug 单")
    void 发现缺陷自动落单() {
        AgentWorkPackage work = claimed();

        service.report(work.runId(), AGENT,
                ExecReport.of("DONE", "改完了", List.of(), null, List.of(
                        new ReportedBug("分页错位", "MINOR", "翻页后重复",
                                "src/page.ts", "offset 计算错误"))),
                "sess-1", TokenUsage.ZERO, null);

        assertEquals(TaskStatus.DONE, tasks.get(work.taskId()).getStatus());
        assertEquals(1, bugs.all().size());
        assertEquals("分页错位", bugs.all().get(0).getTitle());
        assertEquals(work.taskId(), bugs.all().get(0).getSourceTaskId().orElseThrow().value());
    }

    @Test
    @DisplayName("出参读不懂按失败处理，而且失败原因写进摘要")
    void 出参读不懂按失败处理() {
        AgentWorkPackage work = claimed();

        AgentReportOutcome outcome = service.report(work.runId(), AGENT,
                ExecReport.of("BLOCKED", "停下来了", List.of(), null, List.of()),
                "sess-1", TokenUsage.ZERO, null);

        assertEquals(AgentReportOutcome.Outcome.FAILED, outcome.outcome());
        assertEquals(TaskStatus.FAILED, tasks.get(work.taskId()).getStatus());
        assertTrue(outcome.note().contains("人不知道要批什么"), outcome.note());
    }

    @Test
    @DisplayName("别人不能替这条记录上报")
    void 别人不能代报() {
        AgentWorkPackage work = claimed();

        assertThrows(DomainException.class, () -> service.report(work.runId(), "冒名者",
                report("DONE", null), "sess-x", TokenUsage.ZERO, null));
    }

    @Test
    @DisplayName("心跳续租并告诉执行者接着干")
    void 心跳报接着干() {
        AgentWorkPackage work = claimed();

        AgentRunSnapshot snapshot = service.heartbeat(work.runId(), AGENT);

        assertEquals(AgentRunSnapshot.NextAction.KEEP_WORKING, snapshot.nextAction());
        assertFalse(runs.get(work.runId()).getLeaseExpiresAt().isBefore(work.leaseExpiresAt()), "心跳该把租约往后推");
    }

    @Test
    @DisplayName("挂起之后待批：心跳告诉执行者停手等着")
    void 挂起后等批复() {
        AgentWorkPackage work = claimed();
        service.report(work.runId(), AGENT, report("BLOCKED", decision("L3", true)), "sess-1", TokenUsage.ZERO, null);

        AgentRunSnapshot snapshot = service.heartbeat(work.runId(), AGENT);

        assertEquals(AgentRunSnapshot.NextAction.WAIT_DECISION, snapshot.nextAction());
        assertEquals("PENDING", snapshot.decisionStatus());
    }

    @Test
    @DisplayName("批准之后：心跳让执行者重新认领，把带结论的活接回去")
    void 批准后叫人重新认领() {
        AgentWorkPackage work = claimed();
        AgentReportOutcome blocked = service.report(work.runId(), AGENT,
                report("BLOCKED", decision("L3", true)), "sess-1", TokenUsage.ZERO, null);
        decisions.get(blocked.decisionId()).approve("按建议执行", "同意");

        assertEquals(AgentRunSnapshot.NextAction.CLAIM_AGAIN, service.heartbeat(work.runId(), AGENT).nextAction());
    }

    @Test
    @DisplayName("驳回之后：心跳让执行者收工，别再动了")
    void 驳回后收工() {
        AgentWorkPackage work = claimed();
        AgentReportOutcome blocked = service.report(work.runId(), AGENT,
                report("BLOCKED", decision("L3", true)), "sess-1", TokenUsage.ZERO, null);
        decisions.get(blocked.decisionId()).reject("按建议执行", "不同意");

        assertEquals(AgentRunSnapshot.NextAction.STOP, service.heartbeat(work.runId(), AGENT).nextAction());
    }

    @Test
    @DisplayName("续跑：批准之后重新认领，指令里带着「谁拍的板、批了什么」")
    void 批准后重新认领() {
        AgentWorkPackage work = claimed();
        AgentReportOutcome blocked = service.report(work.runId(), AGENT,
                report("BLOCKED", decision("L3", true)), "sess-1", TokenUsage.ZERO, null);
        Decision decision = decisions.get(blocked.decisionId());
        decision.approve("加适配层", "调用方太多");
        tasks.get(work.taskId()).resume();

        AgentWorkPackage resumed = service.claim(AGENT, "sess-2", null, work.taskId()).orElseThrow();

        assertEquals(2, resumed.attemptNo());
        assertTrue(resumed.instruction().contains("人工裁决为：加适配层"), resumed.instruction());
        assertEquals(1, branches.switched.size(), "续跑必须切回原来那条分支");
    }

    @Test
    @DisplayName("租约到期：判失联、放开任务，并留下一条能让人看懂的原因")
    void 租约到期回收() throws Exception {
        service = serviceWithLease(Duration.ofMillis(1));
        AgentWorkPackage work = claimed();
        Thread.sleep(10);

        assertEquals(1, service.releaseExpiredLeases());
        assertEquals(RunStatus.FAILED, runs.get(work.runId()).getStatus());
        assertEquals(TaskStatus.FAILED, tasks.get(work.taskId()).getStatus());
        assertTrue(runs.get(work.runId()).getSummary().contains("失联"), runs.get(work.runId()).getSummary());
        assertTrue(service.claim("another-agent", "sess-2", null, null).isEmpty(),
                "任务已经判失败，不会自动再被领走");
    }

    @Test
    @DisplayName("显式指定 RUNNING 任务也不能绕过活跃执行再次认领")
    void 活跃执行不可重复认领() {
        Task task = ExecutionFakes.task(7L, PROJECT, TaskStatus.RUNNING);
        tasks.add(task);
        runs.save(TaskRun.start(TaskId.of(7L), 1, "main"));

        DomainException error = assertThrows(DomainException.class,
                () -> service.claim(AGENT, "sess-2", null, 7L));

        assertTrue(error.getMessage().contains("不可认领"), error.getMessage());
        assertEquals(1, runs.all().size(), "不能创建第二条活跃执行记录");
    }

    @Test
    @DisplayName("设备认领后租约绑定设备，其他设备不能续租或上报")
    void 设备租约绑定认领设备() {
        devices.add(onlineDevice("device-1", true, Instant.now()));
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));

        AgentWorkPackage work = service.claim(
                AGENT, "sess-device", null, 7L, "device-1").orElseThrow();

        assertEquals("device-1", runs.get(work.runId()).getDeviceId());
        assertNotNull(runs.get(work.runId()).getLastHeartbeatAt());
        assertThrows(DomainException.class,
                () -> service.heartbeat(work.runId(), AGENT, "device-2"));
        assertThrows(DomainException.class,
                () -> service.report(work.runId(), AGENT, "device-2",
                        ExecReport.of("DONE", "冒充上报", List.of(), null, List.of()),
                        "sess-device", TokenUsage.ZERO, null));

        AgentRunSnapshot snapshot = service.heartbeat(work.runId(), AGENT, "device-1");
        assertEquals(AgentRunSnapshot.NextAction.KEEP_WORKING, snapshot.nextAction());
    }

    @Test
    @DisplayName("设备实例重启恢复时同时释放进程内执行位")
    void 设备重启释放执行位() {
        concurrencyGate.updateLimit(1);
        devices.add(onlineDevice("device-1", true, Instant.now()));
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        AgentWorkPackage work = service.claim(
                AGENT, "sess-device", null, 7L, "device-1").orElseThrow();
        assertFalse(concurrencyGate.hasAvailableSlot());

        int recovered = service.recover("device-1", "设备代理进程已重启");

        assertEquals(1, recovered);
        assertEquals(RunStatus.INTERRUPTED, runs.get(work.runId()).getStatus());
        assertEquals(TaskStatus.QUEUED, tasks.get(7L).getStatus());
        assertTrue(concurrencyGate.hasAvailableSlot());
    }

    @Test
    @DisplayName("设备离线或 Codex 不可用时接单前失败，不创建执行记录")
    void 设备自检失败不创建执行() {
        devices.add(onlineDevice("offline", true, Instant.now().minusSeconds(60)));
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));

        DomainException offline = assertThrows(DomainException.class,
                () -> service.claim(AGENT, "sess-offline", null, 7L, "offline"));
        assertTrue(offline.getMessage().contains("离线"), offline.getMessage());
        assertTrue(runs.all().isEmpty());

        devices.add(onlineDevice("no-codex", false, Instant.now()));
        DomainException noCodex = assertThrows(DomainException.class,
                () -> service.claim(AGENT, "sess-no-codex", null, 7L, "no-codex"));
        assertTrue(noCodex.getMessage().contains("Codex 不可用"), noCodex.getMessage());
        assertTrue(runs.all().isEmpty());
    }

    @Test
    @DisplayName("项目目录不存在时接单前失败，不消耗执行位")
    void 项目目录不存在不创建执行() throws Exception {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        Files.delete(projectPath(1L));

        DomainException error = assertThrows(DomainException.class,
                () -> service.claim(AGENT, "sess-no-dir", null, 7L));

        assertTrue(error.getMessage().contains("项目目录不存在"), error.getMessage());
        assertTrue(runs.all().isEmpty());
        assertTrue(concurrencyGate.hasAvailableSlot());
    }

    private AgentWorkPackage claimed() {
        tasks.add(ExecutionFakes.task(7L, PROJECT, TaskStatus.PENDING));
        return service.claim(AGENT, "sess-1", null, null).orElseThrow();
    }

    private com.pydlove.regent.project.domain.repository.ProjectProfile projectProfile(long id) {
        try {
            Files.createDirectories(projectPath(id));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        return new com.pydlove.regent.project.domain.repository.ProjectProfile(
                ProjectId.of(id), "p" + id, "项目 " + id, projectPath(id).toString(),
                DecisionLevel.L1, true);
    }

    private Path projectPath(long id) {
        return tempDir.resolve("project-" + id);
    }

    private static ExecutionDevice onlineDevice(String deviceId,
                                                boolean codexAvailable,
                                                Instant seenAt) {
        return ExecutionDevice.heartbeat(
                null,
                UserId.of(1L),
                deviceId,
                "测试设备-" + deviceId,
                "darwin",
                "regent-device@" + deviceId,
                "instance-" + deviceId,
                new ExecutionDevice.CodexStatus(
                        codexAvailable,
                        "/usr/local/bin/codex",
                        "codex-cli 1.0",
                        codexAvailable ? "Codex 可执行文件可用" : "Codex 未安装",
                        seenAt),
                "R100B002",
                "R100B002",
                seenAt);
    }

    private static ExecReport report(String status, ExecReport.Decision decision) {
        return ExecReport.of(status, "这一轮做了什么", List.of("pom.xml"), decision, List.of());
    }

    private static ExecReport.Decision decision(String level, boolean reversible) {
        return new ExecReport.Decision(level, "要不要动依赖？", List.of("动", "不动"), "不动", reversible, "构建");
    }

    /** 上限取自服务实现，测试里只关心「用掉一轮之后还剩几轮」。 */
    private static final class ServiceRounds {
        private static final int LEFT_AFTER_FIRST = AgentRunService.MAX_DECISION_ROUNDS - 1;
    }

    private static void assertNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNull(value);
    }

    private static void assertNull(Object value, String message) {
        org.junit.jupiter.api.Assertions.assertNull(value, message);
    }
}
