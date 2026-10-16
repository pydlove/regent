package com.pydlove.regent.execution.infrastructure.codex;

import com.pydlove.regent.execution.domain.model.ExecRequest;
import com.pydlove.regent.execution.domain.model.ExecResult;
import com.pydlove.regent.execution.domain.model.RunStatus;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.execution.domain.port.RunEventListener;
import com.pydlove.regent.shared.kernel.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * codex CLI 适配器 —— 整个防腐层最厚的一层。
 *
 * <p>关于它为什么存在，最好的说明是它挡掉了什么：进程管理、超时杀进程、stdout 与 stderr 的分流、
 * JSONL 逐行读取、末条消息落盘与解析、退出码语义、「哪些情况算成功」。这些事情里
 * 任何一件渗进 {@code regent-core}，换执行器时都要重写一遍核心域。
 *
 * <h2>几个不显然的取舍</h2>
 * <ul>
 *   <li><b>指令走 stdin，不进 argv。</b>JVM 用平台默认字符集编码 argv，而它常常不是 UTF-8
 *       （{@code LANG} 没设、surefire 里、systemd 下都见过 {@code US-ASCII}）。那种环境下
 *       中文指令会在起进程的那一刻被逐字替换成 {@code ?}，而且不报任何错 —— 模型收到的是一堆
 *       问号，平台却以为派发成功。走 stdin 就没有这道转换：字节由我们自己按 UTF-8 写。</li>
 *   <li><b>stderr 重定向到文件而不是管道。</b>两者都读会引入两线程互锁；只读 stdout 而不管 stderr，
 *       管道缓冲区写满时子进程会卡死。落文件是最省事又不会卡的选择。</li>
 *   <li><b>stdout 在独立线程里读，事件却在调用线程上派发。</b>读必须异步，否则子进程不输出时
 *       超时判定也跟着卡住；但回调不能留在读线程上 —— 调用方八成正处在一个事务里，读线程拿的是
 *       另一条连接，写 run_event 时会在父行 {@code task_run} 的锁上等到天黑。事件进队列、
 *       调用线程边等边取，两个问题一起解决。</li>
 *   <li><b>resume 不带 {@code -C} 与 {@code -s}。</b>{@code codex exec resume} 没有这两个参数，
 *       工作目录靠子进程的 cwd、沙箱模式靠 {@code -c} 覆盖。这一点是从 {@code --help} 里
 *       逐条核对出来的，不是猜的。</li>
 *   <li><b>读不懂出参不要抛异常。</b>它是一份需要被记录下来的结论，会变成一条失败的执行记录，
 *       而不是让工作线程崩掉、任务永远停在 RUNNING。</li>
 *   <li><b>附件走 {@code -i}，一个文件一个参数。</b>{@code exec} 与 {@code resume} 都支持它
 *       （{@code codex exec resume --help} 里写着「attach to the prompt sent after resuming」），
 *       所以续跑时图片仍然跟着指令走。路径由平台生成，只有 UUID 与扩展名 ——
 *       原文件名可能带中文，而 argv 的编码随平台字符集走，落成问号时 codex 就找不到文件了。</li>
 * </ul>
 */
public class CodexCliAdapter implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(CodexCliAdapter.class);

    private static final String SANDBOX_MODE = "workspace-write";
    private static final int MAX_DIAGNOSTIC_CHARS = 2000;

    /** 等待子进程时的轮询间隔：够密，事件基本实时落到调用方手里；够疏，不会空转烧 CPU。 */
    private static final long POLL_MILLIS = 200;

    private final String command;
    private final Path runDirectory;
    private final SchemaFileProvider schemaFileProvider;
    private final CodexJsonlParser parser = new CodexJsonlParser();

    public CodexCliAdapter(String command, Path runDirectory, SchemaFileProvider schemaFileProvider) {
        this.command = command;
        this.runDirectory = runDirectory;
        this.schemaFileProvider = schemaFileProvider;
    }

    @Override
    public ExecResult execute(ExecRequest request, RunEventListener listener) {
        Path outputFile;
        Path errorFile;
        Path promptFile;
        try {
            Files.createDirectories(runDirectory);
            outputFile = Files.createTempFile(runDirectory, "result-", ".json");
            errorFile = Files.createTempFile(runDirectory, "stderr-", ".log");
            promptFile = Files.createTempFile(runDirectory, "prompt-", ".txt");
            Files.writeString(promptFile, instruction(request), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return CodexReportTranslator.invalid(null, null, TokenUsage.ZERO,
                    "运行目录不可写：" + e.getMessage(), null);
        }

        ProcessBuilder builder = new ProcessBuilder(commandLine(request, outputFile));
        builder.directory(Path.of(request.projectPath()).toFile());
        builder.redirectError(errorFile.toFile());
        builder.redirectInput(promptFile.toFile());

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            cleanup(outputFile, errorFile, promptFile);
            return CodexReportTranslator.invalid(null, null, TokenUsage.ZERO,
                    "无法启动执行器（" + command + "）：" + e.getMessage(), null);
        }

        log.info("codex 已启动：项目={} 会话={} 超时={}",
                request.projectId(), request.isResume() ? request.resumeThreadId() : "（新会话）", request.timeout());

        StreamSummary summary = new StreamSummary();
        Thread pump = new Thread(() -> pumpStdout(process, summary), "codex-stdout");
        pump.setDaemon(true);
        pump.start();

        boolean finished = await(process, listener, summary, request.timeout().toMillis());
        if (!finished) {
            process.destroy();
            joinQuietly(pump);
            drain(listener, summary);
            process.destroyForcibly();
            log.warn("codex 执行超时，已终止进程：项目={} 会话={}", request.projectId(), summary.threadId());
            return new ExecResult(RunStatus.TIMEOUT, null, summary.threadId(), summary.tokens(),
                    "执行超过 " + request.timeout() + " 未结束，已终止进程", readQuietly(outputFile),
                    false, List.of(), null);
        }
        joinQuietly(pump);
        drain(listener, summary);

        int exitCode = process.exitValue();
        String rawResult = readQuietly(outputFile);
        String stderrTail = tail(readQuietly(errorFile));
        cleanup(outputFile, errorFile, promptFile);
        log.info("codex 已结束：项目={} 退出码={} 会话={} token={}",
                request.projectId(), exitCode, summary.threadId(), summary.tokens().total());

        if (exitCode != 0) {
            return CodexReportTranslator.invalid(exitCode, summary.threadId(), summary.tokens(),
                    "执行器退出码 " + exitCode + "：" + firstLine(stderrTail), rawResult);
        }
        Optional<CodexJsonlParser.CodexReport> report = parser.parseReport(rawResult);
        if (report.isEmpty()) {
            return CodexReportTranslator.invalid(exitCode, summary.threadId(), summary.tokens(),
                    "末条消息不是约定结构的 JSON，无法读出结论", rawResult);
        }
        return CodexReportTranslator.translate(report.get(), exitCode, summary.threadId(),
                summary.tokens(), rawResult);
    }

    /** 交给模型的完整指令：任务本身 + 出参约定。约定必须随指令一起给，否则模型不知道要按什么格式汇报。 */
    private String instruction(ExecRequest request) {
        return request.prompt() + "\n\n---\n\n" + schemaFileProvider.contract();
    }

    /**
     * 命令行。
     *
     * <p>最后的 {@code -} 是位置参数，含义是「指令从 stdin 读」—— 指令绝不能进 argv，
     * 理由见类注释第一条。
     */
    private List<String> commandLine(ExecRequest request, Path outputFile) {
        List<String> args = new ArrayList<>();
        args.add(command);
        args.add("exec");
        if (request.isResume()) {
            args.add("resume");
            args.add(request.resumeThreadId());
            args.add("-c");
            args.add("sandbox_mode=\"" + SANDBOX_MODE + "\"");
        } else {
            args.add("-C");
            args.add(request.projectPath());
            args.add("-s");
            args.add(SANDBOX_MODE);
        }
        for (String image : request.imagePaths()) {
            args.add("-i");
            args.add(image);
        }
        args.add("--json");
        args.add("--output-schema");
        args.add(schemaFileProvider.schemaFile().toString());
        args.add("-o");
        args.add(outputFile.toString());
        args.add("-");
        return args;
    }

    /**
     * 等子进程结束，顺便把攒下的事件派发出去。
     *
     * @return true 表示进程已结束；false 表示超时或调用线程被中断
     */
    private static boolean await(Process process, RunEventListener listener, StreamSummary summary, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            try {
                if (process.waitFor(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            drain(listener, summary);
            if (System.nanoTime() >= deadline) {
                return false;
            }
        }
    }

    /** 读线程只解析、不回调：回调里可能有事务，而事务是绑线程的，见类注释第三条。 */
    private void pumpStdout(Process process, StreamSummary summary) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parser.parse(line).ifPresent(event -> {
                    if (event.threadId() != null) {
                        summary.threadId = event.threadId();
                    }
                    if (event.usage() != null) {
                        summary.tokens = event.usage();
                    }
                    summary.events.add(event);
                });
            }
        } catch (IOException e) {
            log.warn("事件流读取中断：{}", e.getMessage());
        }
    }

    private static void drain(RunEventListener listener, StreamSummary summary) {
        CodexJsonlParser.ParsedEvent event;
        while ((event = summary.events.poll()) != null) {
            deliver(listener, event);
        }
    }

    /** 回调不许把执行带走：事件是旁路记录，写不进去也不该让整次执行停摆。 */
    private static void deliver(RunEventListener listener, CodexJsonlParser.ParsedEvent event) {
        try {
            listener.onEvent(event.type(), event.payloadJson());
        } catch (RuntimeException e) {
            log.warn("事件回调失败（已忽略）：{}", e.getMessage());
        }
    }

    private static void joinQuietly(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(5));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String readQuietly(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void cleanup(Path... files) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.debug("临时文件清理失败：{}", file);
            }
        }
    }

    /** 只取尾部若干字符：报错信息要的是最后一句，而不是整篇构建日志。 */
    private static String tail(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_DIAGNOSTIC_CHARS ? text : text.substring(text.length() - MAX_DIAGNOSTIC_CHARS);
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "（无错误输出）";
        }
        String trimmed = text.strip();
        int end = trimmed.indexOf('\n');
        return end < 0 ? trimmed : trimmed.substring(0, end);
    }

    /**
     * 读线程与调用线程之间的交接。
     *
     * <p>会话 ID 与 token 用量在读线程里更新、在调用线程里读，所以是 volatile；
     * 事件本身走并发队列，调用线程按产出顺序取走 —— 顺序不会乱，因为只有一个生产者。
     */
    private static final class StreamSummary {

        private final Queue<CodexJsonlParser.ParsedEvent> events = new ConcurrentLinkedQueue<>();
        private volatile String threadId;
        private volatile TokenUsage tokens = TokenUsage.ZERO;

        private String threadId() {
            return threadId;
        }

        private TokenUsage tokens() {
            return tokens;
        }
    }
}
