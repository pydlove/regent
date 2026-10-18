package com.pydlove.regent.testing.infrastructure.process;

import com.pydlove.regent.testing.domain.model.TestCaseExecutionRequest;
import com.pydlove.regent.testing.domain.model.TestCaseExecutionResult;
import com.pydlove.regent.testing.domain.model.TestCaseRunStatus;
import com.pydlove.regent.testing.domain.port.TestCaseExecutor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 本机 shell 测试执行器。
 *
 * <p>命令会写进临时脚本再交给 {@code /bin/sh}，不通过 argv 传递。原因与 codex 指令相同：
 * JVM 的 {@code file.encoding} 在 systemd / surefire 下未必是 UTF-8，中文命令经 argv
 * 会被静默替换成问号。上传脚本同理，先在本次运行目录还原，再通过环境变量暴露。
 */
public class ProcessTestCaseExecutor implements TestCaseExecutor {

    private final Path runRoot;
    private final Duration timeout;
    private final int maxOutputBytes;

    public ProcessTestCaseExecutor(Path runRoot, Duration timeout, int maxOutputBytes) {
        this.runRoot = runRoot;
        this.timeout = timeout;
        this.maxOutputBytes = maxOutputBytes;
    }

    @Override
    public TestCaseExecutionResult execute(TestCaseExecutionRequest request) {
        Path runDirectory = null;
        try {
            Files.createDirectories(runRoot);
            runDirectory = Files.createTempDirectory(runRoot, "run-");

            Path uploadedScript = null;
            if (request.scriptContent() != null && request.scriptFilename() != null) {
                uploadedScript = runDirectory.resolve(request.scriptFilename());
                Files.writeString(uploadedScript, request.scriptContent(), StandardCharsets.UTF_8);
            }

            String command = request.command();
            if (command == null || command.isBlank()) {
                command = defaultCommand(request.scriptFilename());
                if (command == null) {
                    return new TestCaseExecutionResult(TestCaseRunStatus.FAILED, null,
                            "没有可执行的测试命令",
                            "上传脚本的扩展名无法推断执行方式，请显式填写执行命令。");
                }
            }
            Path commandFile = runDirectory.resolve("command.sh");
            Files.writeString(commandFile, command + System.lineSeparator(), StandardCharsets.UTF_8);

            ProcessBuilder builder = new ProcessBuilder("/bin/sh", commandFile.toString());
            builder.directory(Path.of(request.projectPath()).toFile());
            builder.redirectErrorStream(true);
            Map<String, String> environment = builder.environment();
            environment.putAll(request.environment());
            environment.put("REGENT_TEST_CASE_ID", Long.toString(request.testCaseId().value()));
            environment.put("REGENT_TEST_TYPE", request.type().name());
            if (request.taskId() == null) {
                environment.remove("REGENT_TASK_ID");
            } else {
                environment.put("REGENT_TASK_ID", Long.toString(request.taskId().value()));
            }
            if (uploadedScript != null) {
                environment.put("REGENT_TEST_FILE", uploadedScript.toString());
            } else {
                environment.remove("REGENT_TEST_FILE");
            }

            Process process = builder.start();
            OutputCollector collector = new OutputCollector(process.getInputStream(), maxOutputBytes);
            Thread pump = new Thread(collector, "regent-test-output");
            pump.setDaemon(true);
            pump.start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                pump.join(2_000);
                String output = collector.text();
                return new TestCaseExecutionResult(TestCaseRunStatus.TIMEOUT, null,
                        "测试超时（" + timeout + "）",
                        output + (output.isBlank() ? "" : "\n") + "[平台] 超过 " + timeout + "，进程已终止");
            }

            pump.join(2_000);
            int exitCode = process.exitValue();
            String output = collector.text();
            if (exitCode == 0) {
                return new TestCaseExecutionResult(TestCaseRunStatus.SUCCESS, 0,
                        "测试通过（退出码 0）", output);
            }
            return new TestCaseExecutionResult(TestCaseRunStatus.FAILED, exitCode,
                    "测试失败（退出码 " + exitCode + "）", output);
        } catch (IOException e) {
            return new TestCaseExecutionResult(TestCaseRunStatus.FAILED, null,
                    "测试进程启动失败：" + messageOf(e), e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TestCaseExecutionResult(TestCaseRunStatus.INTERRUPTED, null,
                    "测试执行被中断", e.toString());
        } finally {
            deleteRecursively(runDirectory);
        }
    }

    private static String defaultCommand(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "sh" -> "sh \"$REGENT_TEST_FILE\"";
            case "js", "mjs", "cjs" -> "node \"$REGENT_TEST_FILE\"";
            case "py" -> "python3 \"$REGENT_TEST_FILE\"";
            default -> null;
        };
    }

    private static String messageOf(IOException e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }

    private static void deleteRecursively(Path root) {
        if (root == null) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 临时目录清理失败不影响测试结论
                        }
                    });
        } catch (IOException ignored) {
            // 同上
        }
    }

    private static final class OutputCollector implements Runnable {

        private final InputStream input;
        private final int maxBytes;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean truncated;

        private OutputCollector(InputStream input, int maxBytes) {
            this.input = input;
            this.maxBytes = maxBytes;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8_192];
            try (input) {
                int read;
                while ((read = input.read(chunk)) >= 0) {
                    append(chunk, read);
                }
            } catch (IOException ignored) {
                // 进程被终止时读管道会抛错，已有输出仍然有价值
            }
        }

        private synchronized void append(byte[] chunk, int length) {
            int remaining = maxBytes - buffer.size();
            if (remaining > 0) {
                buffer.write(chunk, 0, Math.min(length, remaining));
            }
            if (length > remaining) {
                truncated = true;
            }
        }

        private synchronized String text() {
            String value = buffer.toString(StandardCharsets.UTF_8);
            return truncated ? value + "\n…（输出已截断）" : value;
        }
    }
}
