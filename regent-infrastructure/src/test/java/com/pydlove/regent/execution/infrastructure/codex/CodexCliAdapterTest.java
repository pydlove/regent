package com.pydlove.regent.execution.infrastructure.codex;

import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.AgentAccessMode;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 适配器对着一个假 codex 脚本跑 —— 不碰模型，只验证「进程起没起对、事件流读没读懂、出参翻译对不对」。
 *
 * <p>这一段是最容易悄悄坏掉的地方：它只在真跑任务时才被触发，一旦坏了，症状是「任务莫名失败」，
 * 而报错现场早就随子进程消失了。所以这里把超时、非零退出、出参读不懂这几条支路逐个跑一遍，
 * 再加上两条踩过坑的回归：指令必须走 stdin（argv 会被平台字符集打烂）、事件必须在调用线程上派发
 * （读线程写库会撞上调用方未提交的事务）。
 *
 * <p>注意断言里的比较全部发生在 JVM 内部：控制台把中文打成问号不影响这里的结论，
 * 但也正因为如此，「指令完整送达」这件事只能靠假执行器把收到的字节原样记下来验证。
 */
class CodexCliAdapterTest {

    private static final String 指令原文 = "把 README 的措辞改得更准确，别动别的文件";

    private static final String DONE_REPORT =
            "{\"status\":\"DONE\",\"summary\":\"改完了\",\"changedFiles\":[\"README.md\"],\"decision\":null,"
                    + "\"bugs\":[{\"title\":\"分页错位\",\"severity\":\"MINOR\",\"reproduction\":\"翻页\","
                    + "\"filePath\":\"src/page.ts\",\"detail\":\"offset 算错\"}]}";

    private static final String 事件流 = """
            printf '{"type":"thread.started","thread_id":"th-1"}\\n'
            printf '{"type":"turn.started"}\\n'
            printf '{"type":"turn.completed","usage":{"input_tokens":100,"cached_input_tokens":20,"output_tokens":30}}\\n'
            """;

    private Path workspace;
    private Path runs;
    private Path script;
    private Path argsFile;
    private Path promptsFile;
    private Path environmentFile;

    @BeforeEach
    void 准备环境() throws IOException {
        workspace = Files.createTempDirectory("regent-adapter-");
        runs = Files.createTempDirectory("regent-runs-");
        script = workspace.resolve("fake-codex.sh");
        argsFile = workspace.resolve("args.log");
        promptsFile = workspace.resolve("prompts.log");
        environmentFile = workspace.resolve("environment.log");
    }

    @AfterEach
    void 清理() throws IOException {
        deleteRecursively(workspace);
        deleteRecursively(runs);
    }

    @Test
    @DisplayName("新会话：命令行、事件流、token 与出参翻译全部到位")
    void 新会话() throws IOException {
        写脚本(回放(DONE_REPORT) + 事件流 + "exit 0");

        List<String> events = new ArrayList<>();
        ExecResult result = adapter().execute(请求(null), (type, payload) -> events.add(type));

        assertEquals(RunStatus.SUCCESS, result.status());
        assertEquals(0, result.exitCode());
        assertEquals("th-1", result.threadId(), "会话 ID 必须从 thread.started 里取到");
        assertEquals("改完了", result.summary());
        assertEquals(List.of("README.md"), result.changedFiles());
        assertEquals("分页错位", result.bugs().get(0).title());
        assertEquals("MINOR", result.bugs().get(0).severity());
        assertTrue(result.schemaValid());
        assertEquals(100, result.tokens().input(), "token 必须从 turn.completed 的 usage 里取到");
        assertEquals(20, result.tokens().cachedInput());
        assertEquals(30, result.tokens().output());

        assertEquals(List.of("thread.started", "turn.started", "turn.completed"), events,
                "事件类型要原样交给监听者，运行日志面板靠它");

        String args = 参数();
        assertTrue(args.contains("exec"), args);
        assertFalse(args.contains("resume"), "新会话不该带 resume：" + args);
        assertTrue(args.contains("-s workspace-write"), args);
        assertTrue(args.contains("--skip-git-repo-check"), "非 Git 项目目录也要能启动：" + args);
        assertTrue(args.contains("--json"), args);
        assertTrue(args.contains("--output-schema"), args);
    }

    @Test
    @DisplayName("指令走 stdin：argv 里不许出现非 ASCII，中文指令原样送达")
    void 指令走标准输入() throws IOException {
        写脚本(回放(DONE_REPORT) + 事件流 + "exit 0");

        adapter().execute(请求(null), (type, payload) -> {
        });

        String args = 参数();
        assertTrue(args.lines().allMatch(line -> line.chars().allMatch(c -> c < 128)),
                "argv 会被平台默认字符集编码，一旦非 ASCII 就会被打成问号：" + args);
        assertTrue(args.strip().endsWith("-"), "最后一个位置参数应当是「从 stdin 读指令」：" + args);

        String 送达的指令 = 指令().get(0);
        assertTrue(送达的指令.contains(指令原文), "指令必须原样送达：" + 送达的指令);
        assertTrue(送达的指令.contains("Regent 平台上报约定"), "出参约定要随指令一起给：" + 送达的指令);
    }

    @Test
    @DisplayName("事件在调用线程上派发：回调方拿着自己的事务，不该被换到读线程上")
    void 事件在调用线程派发() throws IOException {
        写脚本(回放(DONE_REPORT) + 事件流 + "exit 0");

        Thread 调用线程 = Thread.currentThread();
        List<Thread> 派发线程 = new CopyOnWriteArrayList<>();
        adapter().execute(请求(null), (type, payload) -> 派发线程.add(Thread.currentThread()));

        assertFalse(派发线程.isEmpty(), "事件必须真的派发出去");
        派发线程.forEach(thread -> assertSame(调用线程, thread, "回调跑到了读线程上，调用方的事务和连接都绑不到"));
    }

    @Test
    @DisplayName("续跑：带 resume 与上一轮会话 ID，且不重复 -C")
    void 续跑() throws IOException {
        写脚本(回放(DONE_REPORT) + 事件流 + "exit 0");

        ExecResult result = adapter().execute(请求("th-9"), (type, payload) -> {
        });

        assertEquals(RunStatus.SUCCESS, result.status());
        String args = 参数();
        assertTrue(args.contains("exec resume th-9"), args);
        assertFalse(args.contains("-C "), "resume 子命令没有 -C 参数：" + args);
        assertTrue(args.contains("sandbox_mode=\"workspace-write\""), args);
        assertTrue(args.contains("--skip-git-repo-check"), "续跑同样要允许非 Git 目录：" + args);
    }

    @Test
    @DisplayName("图片附件：每张图各带一个 -i，且排在 --json 之前")
    void 图片附件随指令下发() throws IOException {
        写脚本(回放(DONE_REPORT) + 事件流 + "exit 0");

        // 路径按真实落盘形态给：存储侧的文件名是 UUID + 扩展名，不含中文（见 LocalAttachmentStorage）
        ExecRequest request = new ExecRequest(ProjectId.of(1L), workspace.toString(), 指令原文, null,
                Duration.ofSeconds(30),
                List.of("/var/regent/attachments/task-7/9f2c.png", "/var/regent/attachments/task-7/3a11.jpg"));
        ExecResult result = adapter().execute(request, (type, payload) -> {
        });

        assertEquals(RunStatus.SUCCESS, result.status());
        String args = 参数();
        // 成对断言而不是只看有没有 -i：多一个 -i 或者路径漏了，都会在这里露出来
        assertTrue(args.contains("-i /var/regent/attachments/task-7/9f2c.png"), args);
        assertTrue(args.contains("-i /var/regent/attachments/task-7/3a11.jpg"), args);
        assertTrue(args.indexOf("-i ") < args.indexOf("--json"), "图片参数必须在 --json 之前：" + args);
    }

    @Test
    @DisplayName("没有附件时不带 -i：空参数会让 codex 把下一条参数当成图片路径")
    void 无附件不带图片参数() throws IOException {
        写脚本(回放(DONE_REPORT) + 事件流 + "exit 0");

        adapter().execute(请求(null), (type, payload) -> {
        });

        assertFalse(参数().contains("-i "), 参数());
    }

    @Test
    @DisplayName("只读任务使用 read-only 沙箱：项目资料分析不能改工作区")
    void 只读任务使用只读沙箱() throws IOException {
        写脚本(回放(DONE_REPORT) + 事件流 + "exit 0");
        ExecRequest request = new ExecRequest(
                ProjectId.of(1L), workspace.toString(), 指令原文, null,
                Duration.ofSeconds(30), List.of(), Map.of(), null,
                AgentAccessMode.READ_ONLY);

        adapter().execute(request, (type, payload) -> {
        });

        assertTrue(参数().contains("-s read-only"), 参数());
        assertFalse(参数().contains("workspace-write"), 参数());
    }

    @Test
    @DisplayName("环境变量注入子进程，值不进入 argv 或指令")
    void 注入环境变量() throws IOException {
        写脚本("printf '%s' \"$REGENT_DB_HOST\" > \"" + environmentFile + "\"\n"
                + 回放(DONE_REPORT) + 事件流 + "exit 0");
        ExecRequest request = new ExecRequest(ProjectId.of(1L), workspace.toString(), 指令原文, null,
                Duration.ofSeconds(30), List.of(), Map.of("REGENT_DB_HOST", "db.internal"));

        ExecResult result = adapter().execute(request, (type, payload) -> {
        });

        assertEquals(RunStatus.SUCCESS, result.status());
        assertEquals("db.internal", Files.readString(environmentFile, StandardCharsets.UTF_8));
        assertFalse(参数().contains("db.internal"), 参数());
        assertFalse(指令().get(0).contains("db.internal"), 指令().get(0));
    }

    @Test
    @DisplayName("有任务分支时给子进程注入 Git 引用守卫")
    void 注入Git分支守卫() throws IOException {
        写脚本("printf '%s|%s' \"$GIT_CONFIG_KEY_0\" \"$GIT_CONFIG_VALUE_0\" > \""
                + environmentFile + "\"\n" + 回放(DONE_REPORT) + 事件流 + "exit 0");
        ExecRequest request = new ExecRequest(ProjectId.of(1L), workspace.toString(), 指令原文, null,
                Duration.ofSeconds(30), List.of(), Map.of(), "codex/task-7");

        ExecResult result = adapter().execute(request, (type, payload) -> {
        });

        assertEquals(RunStatus.SUCCESS, result.status());
        String guard = Files.readString(environmentFile, StandardCharsets.UTF_8);
        assertTrue(guard.startsWith("core.hooksPath|"), guard);
        assertTrue(guard.contains("git-guard-"), guard);
    }

    @Test
    @DisplayName("执行器目录注入子进程 PATH：npm 版 codex 靠它找到同目录的 node")
    void 注入执行器目录到PATH() throws IOException {
        写脚本("printf '%s' \"$PATH\" > \"" + environmentFile + "\"\n"
                + 回放(DONE_REPORT) + 事件流 + "exit 0");

        adapter().execute(请求(null), (type, payload) -> {
        });

        String childPath = Files.readString(environmentFile, StandardCharsets.UTF_8);
        String executorDirectory = script.getParent().toAbsolutePath().normalize().toString();
        assertTrue(childPath.equals(executorDirectory)
                        || childPath.startsWith(executorDirectory + java.io.File.pathSeparator),
                "子进程 PATH 必须以执行器目录开头，实际：" + childPath);
    }

    @Test
    @DisplayName("非 JSON 行不算解析失败：降级成 raw 事件，执行照旧成功")
    void 噪音行不影响结论() throws IOException {
        写脚本(回放(DONE_REPORT)
                + "printf 'warning: 构建缓存缺失\\n'\n"
                + 事件流 + "exit 0");

        List<String> events = new ArrayList<>();
        ExecResult result = adapter().execute(请求(null), (type, payload) -> events.add(type));

        assertEquals(RunStatus.SUCCESS, result.status());
        assertEquals("raw", events.get(0), "读不懂的行要降级成 raw，而不是把整次执行判失败");
    }

    @Test
    @DisplayName("退出码非零：判失败，摘要里带上 stderr 的首行")
    void 退出码非零() throws IOException {
        写脚本("printf '模型服务不可用\\n' >&2\nexit 3");

        ExecResult result = adapter().execute(请求(null), (type, payload) -> {
        });

        assertEquals(RunStatus.FAILED, result.status());
        assertEquals(3, result.exitCode());
        assertFalse(result.schemaValid());
        assertTrue(result.summary().contains("模型服务不可用"), result.summary());
    }

    @Test
    @DisplayName("模型错误只出现在 JSONL 事件里时，也要写进摘要")
    void JSONL错误事件进入摘要() throws IOException {
        写脚本("""
                printf '%s\\n' '{"type":"error","message":"{\\"error\\":{\\"message\\":\\"assistant tool_calls 缺少 tool messages\\"}}"}'
                exit 1
                """);

        ExecResult result = adapter().execute(请求(null), (type, payload) -> {
        });

        assertEquals(RunStatus.FAILED, result.status());
        assertTrue(result.summary().contains("tool_calls 缺少 tool messages"), result.summary());
    }

    @Test
    @DisplayName("缺失 rollout 的续跑会话也属于可降级错误")
    void 缺失rollout可降级() {
        assertTrue(CodexCliAdapter.resumeContextInvalid(
                "Error: thread/resume failed: no rollout found for thread id th-broken"));
        assertFalse(CodexCliAdapter.resumeContextInvalid("HTTP 503 服务暂不可用"));
    }

    @Test
    @DisplayName("续跑会话上下文损坏：自动改用新会话并完成")
    void 损坏会话自动降级() throws IOException {
        Path firstCall = workspace.resolve("first-call");
        写脚本("""
                if [ ! -f "%s" ]; then
                  touch "%s"
                  printf '%%s\\n' '{"type":"error","message":"{\\"error\\":{\\"message\\":\\"assistant tool_calls must be followed by tool messages\\"}}"}'
                  exit 1
                fi
                %s
                %s
                exit 0
                """.formatted(firstCall, firstCall, 回放(DONE_REPORT), 事件流));

        List<String> events = new ArrayList<>();
        ExecResult result = adapter().execute(请求("th-broken"), (type, payload) -> events.add(type));

        assertEquals(RunStatus.SUCCESS, result.status(), result.summary());
        assertEquals("th-1", result.threadId());
        List<String> calls = 假执行器收到的(argsFile);
        assertEquals(2, calls.size(), calls.toString());
        assertTrue(calls.get(0).contains("exec resume th-broken"), calls.get(0));
        assertFalse(calls.get(1).contains("resume"), "降级后的第二次调用必须新建会话：" + calls.get(1));
        assertTrue(events.contains("error"), "第一次失败的真实错误仍要保留在事件流：" + events);
    }

    @Test
    @DisplayName("末条消息不是约定结构：判失败，但原文留下来复盘")
    void 出参读不懂() throws IOException {
        写脚本(回放("这是一段自由发挥的散文，没有 JSON") + "exit 0");

        ExecResult result = adapter().execute(请求(null), (type, payload) -> {
        });

        assertEquals(RunStatus.FAILED, result.status());
        assertFalse(result.schemaValid());
        assertNotNull(result.rawResult());
        assertTrue(result.rawResult().contains("散文"), result.rawResult());
    }

    @Test
    @DisplayName("超时：杀进程并回报 TIMEOUT，不让调用方永远等下去")
    void 超时() throws IOException {
        写脚本("sleep 30");

        long startedAt = System.nanoTime();
        ExecResult result = adapter().execute(请求(null, Duration.ofMillis(800)), (type, payload) -> {
        });
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        assertEquals(RunStatus.TIMEOUT, result.status());
        assertNull(result.exitCode());
        assertTrue(elapsedMillis < 15_000, "超时必须在秒级被截断，实际 " + elapsedMillis + "ms");
    }

    @Test
    @DisplayName("启动不了执行器：把原因写进摘要，而不是抛异常")
    void 执行器不存在() {
        CodexCliAdapter broken = new CodexCliAdapter("/nonexistent/codex", runs, new SchemaFileProvider(runs));

        ExecResult result = broken.execute(请求(null), (type, payload) -> {
        });

        assertEquals(RunStatus.FAILED, result.status());
        assertFalse(result.schemaValid());
        assertTrue(result.summary().contains("无法启动执行器"), result.summary());
    }

    @Test
    @DisplayName("平台关闭：终止活动 codex 进程，并把这次执行标记为中断")
    void 关闭时终止活动进程() throws Exception {
        Path started = workspace.resolve("started.log");
        写脚本("printf 'started' > \"%s\"\nsleep 30".formatted(started));
        CodexCliAdapter adapter = adapter();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ExecResult> future = pool.submit(() -> adapter.execute(请求(null), (type, payload) -> {
            }));
            等待文件(started);

            adapter.close();
            ExecResult result = future.get(10, TimeUnit.SECONDS);

            assertEquals(RunStatus.INTERRUPTED, result.status());
            assertTrue(result.summary().contains("平台正在重启"), result.summary());
        } finally {
            adapter.close();
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------

    private ExecRequest 请求(String resumeThreadId) {
        return 请求(resumeThreadId, Duration.ofSeconds(30));
    }

    private ExecRequest 请求(String resumeThreadId, Duration timeout) {
        return new ExecRequest(ProjectId.of(1L), workspace.toString(), 指令原文, resumeThreadId, timeout,
                List.of());
    }

    private CodexCliAdapter adapter() {
        return new CodexCliAdapter(script.toString(), runs, new SchemaFileProvider(runs));
    }

    private static void 等待文件(Path file) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(file) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(Files.exists(file), "假 codex 没有启动");
    }

    /** 假执行器收到的参数，按调用切开。 */
    private List<String> 假执行器收到的(Path file) throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<String> calls = new ArrayList<>();
        for (String chunk : Files.readString(file, StandardCharsets.UTF_8).split("### CALL")) {
            if (!chunk.isBlank()) {
                calls.add(chunk.strip());
            }
        }
        return calls;
    }

    private String 参数() throws IOException {
        return String.join("\n", 假执行器收到的(argsFile));
    }

    private List<String> 指令() throws IOException {
        return 假执行器收到的(promptsFile);
    }

    /** 把 {@code -o} 指定的文件写成给定的末条消息 —— 真实 codex 也是这么落盘的。 */
    private String 回放(String report) {
        return "printf '%s' '" + report + "' > \"$out\"\n";
    }

    /**
     * 造假执行器。所有脚本共用同一个开头：把 argv 落盘、把 stdin（也就是指令）落盘，
     * 再从参数里挑出 {@code -o} 的目标 —— 后半段由各用例自己决定。
     */
    private void 写脚本(String body) throws IOException {
        String header = """
                #!/bin/bash
                set -u
                printf '### CALL\\n%%s\\n' "$*" >> "%s"
                printf '### CALL\\n' >> "%s"
                cat >> "%s"
                out=""
                while [ $# -gt 0 ]; do
                  case "$1" in
                    -o) out="$2"; shift 2 ;;
                    *) shift ;;
                  esac
                done
                """.formatted(argsFile, promptsFile, promptsFile);
        Files.writeString(script, header + body + "\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
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
}
