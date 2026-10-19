package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskAdjustment;
import com.pydlove.regent.execution.domain.model.TaskAdjustmentStatus;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.model.DecisionRaise;
import com.pydlove.regent.governance.domain.model.DecisionStatus;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.EnvironmentEntry;
import com.pydlove.regent.project.domain.model.EnvironmentScope;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.shared.kernel.TokenUsage;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行生命周期测试 —— 本轮改动里最需要被守住的逻辑。
 *
 * <p>三类结论都要走一遍：跑通、跑挂、停下来等人。而「停下来等人」又分两种命运：
 * 落在阈值内就自己拍板并接着跑，超出阈值才真的挂起。
 */
class TaskRunServiceTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);
    private static final TaskId TASK = TaskId.of(1L);

    private final ExecutionFakes.Tasks tasks = new ExecutionFakes.Tasks();
    private final ExecutionFakes.Runs runs = new ExecutionFakes.Runs();
    private final ExecutionFakes.Events events = new ExecutionFakes.Events();
    private final ExecutionFakes.Adjustments adjustments = new ExecutionFakes.Adjustments();
    private final ExecutionFakes.Decisions decisions = new ExecutionFakes.Decisions();
    private final ExecutionFakes.Bugs bugs = new ExecutionFakes.Bugs();
    private final ExecutionFakes.Rules rules = new ExecutionFakes.Rules();
    private final ExecutionFakes.Projects projects = new ExecutionFakes.Projects();
    private final ExecutionFakes.Attachments attachments = new ExecutionFakes.Attachments();
    private final ExecutionFakes.Environments environments = new ExecutionFakes.Environments();
    private final ExecutionFakes.TaskPlugins plugins = new ExecutionFakes.TaskPlugins();
    private final ExecutionFakes.Executor executor = new ExecutionFakes.Executor();
    private final ExecutionFakes.Branches branches = new ExecutionFakes.Branches();

    private TaskRunService service;

    @BeforeEach
    void setUp() {
        tasks.wire(runs);
        events.wire(runs);
        projects.put(ExecutionFakes.Projects.profile(1L, DecisionLevel.L1, true));
        service = new TaskRunService(tasks, runs, events, attachments, projects, environments, plugins,
                new RunSettlementService(tasks, runs, adjustments, decisions,
                        new DecisionRaiseService(rules, decisions), bugs, taskId -> {
                        }, new ExecutionFakes.Transactions()),
                executor, branches, Duration.ofMinutes(30));
    }

    /** 已认领的任务：分支已切、等待第一次执行。 */
    private Task claimedTask() {
        Task task = ExecutionFakes.task(1L, PROJECT, TaskStatus.RUNNING);
        tasks.add(task);
        return task;
    }

    private Task claimedTaskWithoutBranch() {
        Task task = Task.manual(PROJECT, "任务 1", "把这件事做完", 0);
        task.assignIdentity(TaskId.of(1L));
        task.dispatch(null);
        tasks.add(task);
        return task;
    }

    @Nested
    @DisplayName("跑到底")
    class Terminal {

        @Test
        void 成功时任务完成且会话回填() {
            claimedTask();
            executor.script(ExecutionFakes.Executor.success("改完了"));

            TaskRun run = service.execute(TASK);

            assertEquals(RunStatus.SUCCESS, run.getStatus());
            assertEquals(TaskStatus.DONE, tasks.get(1L).getStatus());
            assertEquals(1, run.getAttemptNo());
            assertEquals("改完了", run.getSummary());
            assertEquals("th-fake-1", tasks.get(1L).getCodexThreadId().orElseThrow());
            assertFalse(events.all().isEmpty(), "事件流要落在库里，运行日志面板就靠它");
        }

        @Test
        void AI请求隔离分支时在执行后创建并记录() {
            claimedTask();
            executor.script(new ExecResult(RunStatus.SUCCESS, 0, "th-fake-1", TokenUsage.ZERO,
                    "大功能完成", null, true, List.of("src/App.tsx"), List.of(), null,
                    new ExecResult.BranchIntent(true, "feature/big-ui", "跨模块大改")));

            service.execute(TASK);

            assertEquals(List.of("feature/big-ui"), branches.created);
            assertEquals("feature/big-ui", tasks.get(1L).getBranchName().orElseThrow());
            assertEquals(TaskStatus.DONE, tasks.get(1L).getStatus());
        }

        @Test
        void 非Git目录忽略AI分支请求且任务正常完成() {
            branches.current = null;
            claimedTaskWithoutBranch();
            executor.script(new ExecResult(RunStatus.SUCCESS, 0, "th-fake-1", TokenUsage.ZERO,
                    "大功能完成", null, true, List.of("src/App.tsx"), List.of(), null,
                    new ExecResult.BranchIntent(true, "feature/big-ui", "跨模块大改")));

            TaskRun run = service.execute(TASK);

            assertTrue(branches.created.isEmpty());
            assertTrue(tasks.get(1L).getBranchName().isEmpty());
            assertNull(run.getBranchName());
            assertEquals(TaskStatus.DONE, tasks.get(1L).getStatus());
        }

        @Test
        void 失败时任务失败() {
            claimedTask();
            executor.script(ExecutionFakes.Executor.failed("测试没过"));

            TaskRun run = service.execute(TASK);

            assertEquals(RunStatus.FAILED, run.getStatus());
            assertEquals(TaskStatus.FAILED, tasks.get(1L).getStatus());
        }

        @Test
        void 平台关闭中断执行时任务留在执行中等待下次启动续跑() {
            claimedTask();
            executor.script(new ExecResult(RunStatus.INTERRUPTED, null, "th-closed", TokenUsage.ZERO,
                    "平台正在重启", null, true, List.of(), List.of(), null));

            TaskRun run = service.execute(TASK);

            assertEquals(RunStatus.INTERRUPTED, run.getStatus());
            assertEquals(TaskStatus.RUNNING, tasks.get(1L).getStatus(),
                    "这不是业务失败，不能把任务判成 FAILED");
        }

        @Test
        void 执行结束后人终止了任务则不覆盖人的决定() {
            claimedTask();
            executor.onExecute = request -> tasks.get(1L).cancel();
            executor.script(ExecutionFakes.Executor.success("做完了"));

            TaskRun run = service.execute(TASK);

            assertEquals(RunStatus.SUCCESS, run.getStatus(), "执行本身确实成功了，记录如实写");
            assertEquals(TaskStatus.CANCELLED, tasks.get(1L).getStatus(), "但不能把人工终止覆盖回 DONE");
        }

        @Test
        @DisplayName("执行期间新增调整：成功也不收场，带着调整续跑同一会话")
        void 执行期间新增调整后自动续跑() {
            claimedTask();
            executor.onExecute = request -> {
                if (executor.requests.size() == 1) {
                    adjustments.save(TaskAdjustment.temporary(TASK, "再补一套边界测试"));
                }
            };
            executor.script(
                    ExecutionFakes.Executor.success("第一轮做完了"),
                    ExecutionFakes.Executor.success("调整也处理完了"));

            TaskRun run = service.execute(TASK);

            assertEquals(RunStatus.SUCCESS, run.getStatus());
            assertEquals(TaskStatus.DONE, tasks.get(1L).getStatus());
            assertEquals(2, executor.requests.size());
            assertTrue(executor.requests.get(1).isResume());
            assertTrue(executor.requests.get(1).prompt().contains("再补一套边界测试"),
                    executor.requests.get(1).prompt());
            assertEquals(TaskAdjustmentStatus.APPLIED, adjustments.all().get(0).getStatus());
            assertEquals(run.getId(), adjustments.all().get(0).getAppliedRunId().orElseThrow());
        }

        @Test
        void 连续决策超过上限时停下来() {
            claimedTask();
            for (int i = 0; i < TaskRunService.MAX_DECISION_ROUNDS; i++) {
                executor.script(ExecutionFakes.Executor.blocked(DecisionLevel.L1, true));
            }

            TaskRun run = service.execute(TASK);

            assertEquals(RunStatus.FAILED, run.getStatus());
            assertTrue(run.getSummary().contains("连续决策"), "摘要要说清为什么停：" + run.getSummary());
            assertEquals(TaskStatus.FAILED, tasks.get(1L).getStatus());
        }

        @Test
        void 任务不存在时不写任何记录() {
            assertThrows(EntityNotFoundException.class, () -> service.execute(TaskId.of(99L)));
            assertTrue(runs.all().isEmpty());
        }
    }

    @Nested
    @DisplayName("决策")
    class Decisioning {

        @Test
        void 超出阈值时建单挂起并阻塞任务() {
            claimedTask();
            executor.script(ExecutionFakes.Executor.blocked(DecisionLevel.L2, true));

            TaskRun run = service.execute(TASK);

            assertEquals(RunStatus.BLOCKED, run.getStatus());
            assertEquals(TaskStatus.BLOCKED, tasks.get(1L).getStatus());

            Decision decision = decisions.all().get(0);
            assertEquals(DecisionStatus.PENDING, decision.getStatus());
            assertEquals(DecisionLevel.L2, decision.getEffectiveLevel());
            assertEquals(decision.getId(), tasks.get(1L).getBlockingDecisionId().orElseThrow());
            assertTrue(events.all().stream().map(RunEvent::type).anyMatch("platform.decision"::equals),
                    "日志里要能看出「为什么停下来了」");
            assertEquals(1, executor.requests.size(), "要人工拍板时不能再自己往下跑");
        }

        @Test
        void 阈值内且可逆时自主决断并立刻续跑() {
            claimedTask();
            executor.script(
                    ExecutionFakes.Executor.blocked(DecisionLevel.L1, true),
                    ExecutionFakes.Executor.success("按建议做完了"));

            TaskRun run = service.execute(TASK);

            assertEquals(RunStatus.SUCCESS, run.getStatus());
            assertEquals(TaskStatus.DONE, tasks.get(1L).getStatus());
            assertEquals(1, run.getAttemptNo(), "自动决断的续跑是同一行记录，不算新尝试");

            Decision decision = decisions.all().get(0);
            assertEquals(DecisionStatus.AUTO_RESOLVED, decision.getStatus());

            assertEquals(2, executor.requests.size());
            ExecRequest second = executor.requests.get(1);
            assertTrue(second.isResume(), "第二轮必须续跑同一个会话");
            assertEquals("th-fake-1", second.resumeThreadId());
            assertTrue(second.prompt().contains("决策已归位"), "续跑指令要带上结论：" + second.prompt());
        }

        @Test
        void 不可逆的改动即使阈值内也挂起等人() {
            claimedTask();
            executor.script(ExecutionFakes.Executor.blocked(DecisionLevel.L1, false));

            TaskRun run = service.execute(TASK);

            assertEquals(RunStatus.BLOCKED, run.getStatus());
            assertEquals(DecisionStatus.PENDING, decisions.all().get(0).getStatus());
            assertEquals(TaskStatus.BLOCKED, tasks.get(1L).getStatus());
        }
    }

    @Nested
    @DisplayName("续跑与重试的指令")
    class OpeningPrompt {

        @Test
        @DisplayName("有效环境按项目覆盖全局，值只进进程环境不进指令")
        void injectsEffectiveEnvironment() {
            claimedTask();
            environments.add(EnvironmentEntry.create(
                    EnvironmentScope.GLOBAL, null, "DB_HOST", "global-db", List.of("数据库"), "全局数据库"));
            environments.add(EnvironmentEntry.create(
                    EnvironmentScope.PROJECT, PROJECT, "DB_HOST", "project-db", List.of(), "项目数据库"));
            environments.add(EnvironmentEntry.create(
                    EnvironmentScope.PROJECT, PROJECT, "DB_PASSWORD", "secret",
                    List.of(), "数据库密码", true));
            executor.script(ExecutionFakes.Executor.success("做完了"));

            service.execute(TASK);

            ExecRequest request = executor.requests.get(0);
            assertEquals("project-db", request.environmentVariables().get("DB_HOST"));
            assertEquals("secret", request.environmentVariables().get("DB_PASSWORD"));
            assertTrue(request.prompt().contains("`DB_HOST`"), request.prompt());
            assertTrue(request.prompt().contains("项目数据库"), request.prompt());
            assertFalse(request.prompt().contains("project-db"), request.prompt());
            assertFalse(request.prompt().contains("secret"), request.prompt());
        }

        @Test
        @DisplayName("绑定插件内容进入指令，并记录版本审计事件")
        void injectsBoundPluginContent() {
            claimedTask();
            plugins.add(TASK, ExecutionFakes.TaskPlugins.plugin(
                    9L, "Kafka 读取组件", 3, "class KafkaReader { void read() {} }"));
            executor.script(ExecutionFakes.Executor.success("复用了组件"));

            service.execute(TASK);

            ExecRequest request = executor.requests.get(0);
            assertTrue(request.prompt().contains("Kafka 读取组件"), request.prompt());
            assertTrue(request.prompt().contains("class KafkaReader"), request.prompt());
            assertTrue(request.prompt().contains("不要重新生成已有实现"), request.prompt());
            assertTrue(events.all().stream().anyMatch(event -> event.type().equals("platform.plugins")));
        }

        @Test
        void 批准后用人工的结论作为指令() {
            Task task = claimedTask();
            task.recordThreadId("th-1");
            task.block(DecisionId.of(5L));
            task.resume();
            runs.save(blockedRun());
            decisions.save(approvedDecision());

            executor.script(ExecutionFakes.Executor.success("按人的决定做完了"));
            service.execute(TASK);

            ExecRequest request = executor.requests.get(0);
            assertTrue(request.isResume());
            assertEquals("th-1", request.resumeThreadId());
            assertTrue(request.prompt().contains("人工裁决"), "要说清是人工拍的板：" + request.prompt());
            assertTrue(request.prompt().contains("先不动"), request.prompt());
        }

        @Test
        void 重试时接着上一轮的进展而不是从头再来() {
            Task task = claimedTask();
            task.recordThreadId("th-1");
            TaskRun failed = TaskRun.start(TASK, 1, "codex/task-1");
            failed.finish(ExecutionFakes.Executor.failed("跑挂了"));
            runs.save(failed);

            executor.script(ExecutionFakes.Executor.success("修好了"));
            service.execute(TASK);

            assertTrue(executor.requests.get(0).prompt().contains("平台重试"));
        }

        @Test
        @DisplayName("看上一轮的结局，而不是看「最新一行」——最新一行就是本次这一行")
        void 本次记录不会遮住上一轮的结局() {
            Task task = claimedTask();
            task.recordThreadId("th-1");
            task.beginContinuation();
            runs.save(blockedRun());
            decisions.save(approvedDecision());

            executor.script(ExecutionFakes.Executor.success("按人的决定做完了"));
            service.execute(TASK);

            ExecRequest request = executor.requests.get(0);
            assertTrue(request.prompt().contains("先不动"),
                    "本次记录行号更大、状态是 RUNNING，会遮住上一轮的 BLOCKED："
                            + "判断必须基于落行之前读到的那一行。" + request.prompt());
        }

        private TaskRun blockedRun() {
            TaskRun run = TaskRun.start(TASK, 1, "codex/task-1");
            run.finish(ExecutionFakes.Executor.blocked(DecisionLevel.L2, true));
            return run;
        }

        private Decision approvedDecision() {
            Decision decision = Decision.raise(new DecisionRaise(TASK, PROJECT, DecisionLevel.L2,
                    DecisionLevel.L2, null, "要不要动依赖？", List.of("动", "不动"), "不动", true, "构建"));
            decision.approve("不动", "先不动，下个版本再说");
            return decision;
        }
    }
}
