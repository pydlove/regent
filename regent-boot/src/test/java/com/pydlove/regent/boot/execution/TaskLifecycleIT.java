package com.pydlove.regent.boot.execution;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugSeverity;
import com.pydlove.regent.bug.domain.model.BugStatus;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.execution.application.command.BugFixDispatchService;
import com.pydlove.regent.execution.application.command.TaskAttachmentService;
import com.pydlove.regent.execution.application.command.TaskDispatchService;
import com.pydlove.regent.execution.application.command.UploadedAttachment;
import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskSource;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.governance.application.command.DecisionApprovalService;
import com.pydlove.regent.governance.application.command.ReviewCommand;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.model.DecisionStatus;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 执行链路的端到端集成测试 —— 真实 MySQL、真实 Git 仓库、真实的子进程与管道，
 * 只有 codex 本体换成一个回放固定 JSONL 的假脚本（TECH_STACK.md 七：不真调模型）。
 *
 * <p>为什么不真调模型：一次真实执行要几十秒到几十分钟、还要烧 token，而这段代码要验证的是
 * 「事件流解析、出参翻译、调度认领、状态落库」这些确定性行为 —— 让模型参与进来，
 * 只会把不确定性引进来，让失败原因变得无法归因。
 *
 * <p>假执行器由测试自己写：脚本把每次调用收到的参数记到文件里，于是「第二次到底有没有
 * 带 resume、有没有把人工结论写进指令」这类断言才可验证。
 *
 * <p>刻意命名为 IT 结尾：surefire 认不出这个后缀，因此没有 MySQL 的机器上 mvn verify 依然全绿。
 * 手动跑法：{@code mvn -pl regent-boot -am test -Dtest=TaskLifecycleIT}
 */
@SpringBootTest(properties = {
        "regent.execution.codex-command=${java.io.tmpdir}/regent-it-fake-codex.sh",
        // 附件也写到临时目录：测试图片不该落在开发机的运行时目录里
        "regent.attachments.dir=${java.io.tmpdir}/regent-it-attachments"})
@ActiveProfiles("test")
@Transactional
class TaskLifecycleIT {

    private static final long PROJECT_ID = 9801L;
    private static final Path FAKE_CODEX =
            Path.of(System.getProperty("java.io.tmpdir"), "regent-it-fake-codex.sh");

    private static final String BLOCKED_REPORT = """
            {"status":"BLOCKED","summary":"改动构建插件前需要确认","changedFiles":["pom.xml"],\
            "decision":{"level":"L2","question":"要不要升级构建插件？","options":["升级","先不动"],\
            "recommendation":"先不动","reversible":true,"blastRadius":"构建流程"},"bugs":[]}""";

    /** 最小的合法 PNG：8 字节签名 + 两字节数据，够过魔数校验。 */
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

    private static final String DONE_REPORT = """
            {"status":"DONE","summary":"改完了","changedFiles":["README.md"],"decision":null,"bugs":[]}""";

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskRunRepository taskRunRepository;

    @Autowired
    private RunEventRepository runEventRepository;

    @Autowired
    private DecisionRepository decisionRepository;

    @Autowired
    private TaskDispatchService taskDispatchService;

    @Autowired
    private TaskAttachmentService taskAttachmentService;

    @Autowired
    private DecisionApprovalService decisionApprovalService;

    @Autowired
    private BugFixDispatchService bugFixDispatchService;

    @Autowired
    private BugRepository bugRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Path workspace;
    private Path argsFile;
    private Path callsFile;
    private Path promptsFile;

    /**
     * 同线程执行的工作池。
     *
     * <p>真实环境里执行跑在独立线程上，而那个线程看不到测试事务里尚未提交的行 ——
     * 那会让「派发 → 执行 → 收敛」在这张测试里断成两截。换成同步执行，换来的是能在同一个
     * 事务里断言完整条链路；线程池本身的行为由调度器的单测覆盖。
     */
    @TestConfiguration
    static class SynchronousWorkers {

        @Bean
        @Primary
        ExecutorService synchronousExecutionWorkers() {
            return new AbstractExecutorService() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }

                @Override
                public void shutdown() {
                }

                @Override
                public List<Runnable> shutdownNow() {
                    return List.of();
                }

                @Override
                public boolean isShutdown() {
                    return false;
                }

                @Override
                public boolean isTerminated() {
                    return false;
                }

                @Override
                public boolean awaitTermination(long timeout, TimeUnit unit) {
                    return true;
                }
            };
        }
    }

    @BeforeEach
    void 准备临时仓库() throws IOException {
        workspace = Files.createTempDirectory("regent-it-");
        git("init", "--quiet");
        git("config", "user.email", "it@regent.local");
        git("config", "user.name", "Regent IT");
        Files.writeString(workspace.resolve("README.md"), "初始内容\n", StandardCharsets.UTF_8);
        git("add", "README.md");
        git("commit", "--quiet", "-m", "初始提交");

        argsFile = Files.createTempFile("regent-it-args-", ".log");
        callsFile = Files.createTempFile("regent-it-calls-", ".log");
        promptsFile = Files.createTempFile("regent-it-prompts-", ".log");
        Files.deleteIfExists(callsFile);

        jdbcTemplate.update("""
                INSERT INTO project (id, code, name, path, default_decision_level, enabled, deleted, created_at, updated_at)
                VALUES (?, 'it_lifecycle', 'IT 生命周期项目', ?, 'L1', 1, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE path = VALUES(path), default_decision_level = VALUES(default_decision_level),
                    enabled = 1, deleted = 0
                """, PROJECT_ID, workspace.toString());
    }

    @AfterEach
    void 清理临时产物() throws IOException {
        deleteRecursively(workspace);
        deleteRecursively(Path.of(System.getProperty("java.io.tmpdir"), "regent-it-attachments"));
        Files.deleteIfExists(argsFile);
        Files.deleteIfExists(callsFile);
        Files.deleteIfExists(promptsFile);
        Files.deleteIfExists(FAKE_CODEX);
    }

    @Test
    @DisplayName("跑通到底：派发 → 执行 → 收敛为 DONE，事件流与 token 全部落库")
    void 成功链路() throws IOException {
        Task task = 新建任务("把 README 改一句", "把 README 的措辞改得更准确");
        写假执行器(DONE_REPORT);

        taskDispatchService.dispatch(task.getId());

        Task done = reload(task.getId());
        assertEquals(TaskStatus.DONE, done.getStatus());
        assertEquals(1, done.getAttemptCount());
        assertEquals("th-1", done.getCodexThreadId().orElseThrow(), "会话 ID 要从事件流里回填");
        assertEquals("codex/task-" + task.getId().value(), 当前分支(), "任务必须在自己的分支上执行");

        TaskRun run = taskRunRepository.findLatestByTask(task.getId()).orElseThrow();
        assertEquals(RunStatus.SUCCESS, run.getStatus());
        assertEquals(0, run.getExitCode());
        assertEquals("改完了", run.getSummary());
        assertEquals(List.of("README.md"), run.getChangedFiles());
        assertEquals(100, run.getTokens().input());
        assertEquals(20, run.getTokens().cachedInput());
        assertEquals(30, run.getTokens().output());
        assertTrue(run.isSchemaValid());

        List<RunEvent> events = runEventRepository.findByRun(run.getId());
        assertFalse(events.isEmpty(), "事件流必须落库，运行日志面板依赖它");
        assertEquals(0, events.get(0).seq(), "序号从 0 开始");
        assertTrue(events.stream().anyMatch(event -> "thread.started".equals(event.type())));
    }

    @Test
    @DisplayName("带图片的任务：派发时把附件挂给执行器，一个文件一个 -i")
    void 附件随指令下发() throws IOException {
        Task task = 新建任务("按截图改样式", "把界面改成截图里的样子");
        写假执行器(DONE_REPORT);
        TaskAttachment shot = taskAttachmentService.attach(task.getId(),
                new UploadedAttachment("界面截图.png", "image/png", PNG));

        taskDispatchService.dispatch(task.getId());

        String args = 假执行器收到的调用().get(0);
        assertTrue(args.contains("-i"), "附件要靠 -i 挂给 codex，否则模型看不到图：" + args);
        assertTrue(args.contains(shot.storagePath()), "挂上去的必须是这张图的路径：" + args);
        assertEquals(TaskStatus.DONE, reload(task.getId()).getStatus());
    }

    @Test
    @DisplayName("挂起等人：决策待批 → 人工批准 → 断点续跑 → 完成")
    void 阻塞与续跑链路() throws IOException {
        Task task = 新建任务("升级构建插件", "把构建插件升到最新版");
        写假执行器(BLOCKED_REPORT, DONE_REPORT);

        taskDispatchService.dispatch(task.getId());

        Task blocked = reload(task.getId());
        assertEquals(TaskStatus.BLOCKED, blocked.getStatus(), "超出项目阈值 L1 的 L2 决策必须挂起");
        DecisionId decisionId = blocked.getBlockingDecisionId().orElseThrow();
        Decision decision = decisionRepository.findById(decisionId).orElseThrow();
        assertEquals(DecisionStatus.PENDING, decision.getStatus());
        assertEquals(DecisionLevel.L2, decision.getEffectiveLevel());
        assertEquals(RunStatus.BLOCKED, taskRunRepository.findLatestByTask(task.getId())
                .orElseThrow().getStatus());

        decisionApprovalService.approve(decisionId.value(), ReviewCommand.of("先不动", "等下个版本一起升"));
        assertEquals(TaskStatus.RUNNING, reload(task.getId()).getStatus());

        taskDispatchService.dispatch(task.getId());

        List<String> calls = 假执行器收到的调用();
        assertEquals(2, calls.size(), "假执行器应当被调用两次：首跑与续跑");
        String second = calls.get(1);
        assertTrue(second.contains("resume"), "第二次必须是 resume 子命令：" + second);
        assertTrue(second.contains("th-1"), "续跑要带上一轮记录的会话 ID：" + second);
        assertTrue(second.contains("--json"), "续跑同样要 JSONL 事件流：" + second);

        // 指令走 stdin 而不是 argv：中文进 argv 会被平台字符集打成问号，所以结论只能去 stdin 里找
        List<String> prompts = 假执行器收到的(promptsFile);
        assertEquals(2, prompts.size(), "两次调用各收到一份指令");
        assertTrue(prompts.get(1).contains("先不动"), "续跑指令里要有人拍的结论：" + prompts.get(1));
        assertTrue(prompts.get(1).contains("人工"), "续跑指令要写明是谁拍的板：" + prompts.get(1));

        Task finalTask = reload(task.getId());
        assertEquals(TaskStatus.DONE, finalTask.getStatus());
        assertEquals(2, finalTask.getAttemptCount(), "人工批准之后的续跑是新的一次尝试");
        TaskRun latest = taskRunRepository.findLatestByTask(task.getId()).orElseThrow();
        assertEquals(2, latest.getAttemptNo());
        assertEquals(RunStatus.SUCCESS, latest.getStatus());
    }

    @Test
    @DisplayName("缺陷派发修复任务：创建 BUG_FIX 任务并回填 fix_task_id")
    void 缺陷派发修复任务() {
        Bug bug = Bug.reportManually(
                ProjectId.of(PROJECT_ID),
                "导出文件名乱码",
                BugSeverity.MAJOR,
                "下载含中文名的导出文件",
                "src/export/CsvExporter.java",
                "响应头编码错误");
        bugRepository.save(bug);

        Task task = bugFixDispatchService.dispatchFix(bug.getId());

        assertEquals(TaskSource.BUG_FIX, task.getSource());
        assertEquals(bug.getId(), task.getBugId().orElseThrow());
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        Bug reloaded = bugRepository.findById(bug.getId()).orElseThrow();
        assertEquals(BugStatus.FIXING, reloaded.getStatus());
        assertEquals(task.getId(), reloaded.getFixTaskId().orElseThrow());
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private Task 新建任务(String title, String prompt) {
        Task task = Task.manual(ProjectId.of(PROJECT_ID), title, prompt, 0);
        taskRepository.save(task);
        return task;
    }

    private Task reload(TaskId id) {
        return taskRepository.findById(id).orElseThrow();
    }

    /**
     * 假执行器收到的参数，按调用切分。
     *
     * <p>不能直接按行读：一次调用的参数里包含多行指令，参数条数与行数根本不是一回事。
     * 脚本用 {@code ### CALL} 哨兵标记每次调用的开头，这里按哨兵切开，每段拼成一段文本，
     * 断言就能直接对「这一次调用的全部参数」做包含判断。
     */
    private List<String> 假执行器收到的调用() throws IOException {
        return 假执行器收到的(argsFile);
    }

    private List<String> 假执行器收到的(Path recordFile) throws IOException {
        if (!Files.exists(recordFile)) {
            return List.of();
        }
        String raw = Files.readString(recordFile, StandardCharsets.UTF_8);
        List<String> calls = new ArrayList<>();
        for (String chunk : raw.split("### CALL")) {
            if (!chunk.isBlank()) {
                calls.add(chunk.strip());
            }
        }
        return calls;
    }

    /** 把假执行器写到固定路径上 —— 命令路径在容器启动时就注入完了，只能换脚本内容。 */
    private void 写假执行器(String... responses) throws IOException {
        Files.writeString(FAKE_CODEX, fakeCodexScript(responses), StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(FAKE_CODEX, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private String 当前分支() throws IOException {
        return git("rev-parse", "--abbrev-ref", "HEAD").strip();
    }

    private String git(String... args) throws IOException {
        List<String> command = new ArrayList<>(List.of("git", "-C", workspace.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("git " + String.join(" ", args) + " 失败：" + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("git 命令被中断：" + String.join(" ", args), e);
        }
        return output;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响断言
                }
            });
        }
    }

    /**
     * 假 codex：按调用次数回放准备好的结构化出参，并把收到的参数与指令原样记账。
     *
     * <p>两件东西都要记：argv 用来验证「续跑带没带 resume 与会话 ID」，stdin 用来验证
     * 「人工的结论有没有写进指令」—— 后者只能从 stdin 里看，因为平台的指令本来就走 stdin。
     *
     * <p>参数解析只关心 {@code -o}：真实的 codex 把末条消息写在那里，假执行器照做，
     * 平台才有东西可解析。
     */
    private String fakeCodexScript(String... responses) {
        StringBuilder cases = new StringBuilder();
        for (int i = 0; i < responses.length; i++) {
            cases.append("  ").append(i + 1).append(") RESPONSE='").append(responses[i]).append("' ;;\n");
        }
        cases.append("  *) RESPONSE='").append(responses[responses.length - 1]).append("' ;;\n");
        return """
                #!/bin/bash
                # 假 codex：按调用次数回放固定 JSONL
                set -u
                CALLS="%s"
                ARGS="%s"
                PROMPTS="%s"
                # 每次调用先落一条哨兵行，再把参数逐条落盘：不用 "$*" 拼一行是因为
                # 参数里可能带换行，事后就切不开调用边界了。
                printf '### CALL\\n' >> "$ARGS"
                for a in "$@"; do printf '%%s\\n' "$a" >> "$ARGS"; done
                printf '### CALL\\n' >> "$PROMPTS"
                cat >> "$PROMPTS"
                out=""
                while [ $# -gt 0 ]; do
                  case "$1" in
                    -o) out="$2"; shift 2 ;;
                    *) shift ;;
                  esac
                done
                n=0
                if [ -f "$CALLS" ]; then n=$(cat "$CALLS"); fi
                n=$((n + 1))
                echo "$n" > "$CALLS"
                case "$n" in
                %s
                esac
                printf '%%s' "$RESPONSE" > "$out"
                printf '{"type":"thread.started","thread_id":"th-%%s"}\\n' "$n"
                printf '{"type":"turn.started"}\\n'
                printf '{"type":"turn.completed","usage":{"input_tokens":100,"cached_input_tokens":20,"output_tokens":30}}\\n'
                exit 0
                """.formatted(callsFile, argsFile, promptsFile, cases);
    }
}
