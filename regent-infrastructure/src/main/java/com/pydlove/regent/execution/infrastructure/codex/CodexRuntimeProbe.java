package com.pydlove.regent.execution.infrastructure.codex;

import com.pydlove.regent.execution.domain.model.ExecutorRuntimeStatus;
import com.pydlove.regent.execution.domain.port.ExecutorRuntimeProbe;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 通过「解析 codex 可执行文件 + 执行 {@code codex --version}」检测本机是否真的可用。
 *
 * <p>只看文件存在不够：npm 包装脚本还依赖同目录的 node，文件权限或运行环境坏了同样跑不起来。
 * 检测只启动版本命令，不触发模型调用，也不验证登录状态。
 */
public class CodexRuntimeProbe implements ExecutorRuntimeProbe {

    private static final long TIMEOUT_SECONDS = 5;
    private static final int MAX_MESSAGE_CHARS = 500;

    private final String configuredCommand;

    public CodexRuntimeProbe(String configuredCommand) {
        this.configuredCommand = configuredCommand;
    }

    @Override
    public ExecutorRuntimeStatus check() {
        Instant checkedAt = Instant.now();
        Optional<Path> resolved;
        try {
            resolved = CodexCommandResolver.resolve(configuredCommand);
        } catch (RuntimeException e) {
            return unavailable(checkedAt, "Codex 配置命令无效：" + messageOf(e));
        }
        if (resolved.isEmpty()) {
            return unavailable(checkedAt,
                    "未找到 Codex 可执行文件（配置为 " + configuredCommand + "）");
        }

        Path executable = resolved.get();
        ProcessBuilder builder = new ProcessBuilder(executable.toString(), "--version");
        builder.redirectErrorStream(true);
        String currentPath = builder.environment().getOrDefault("PATH", "");
        Path binDirectory = executable.getParent();
        if (binDirectory != null) {
            builder.environment().put("PATH",
                    currentPath.isBlank()
                            ? binDirectory.toString()
                            : binDirectory + File.pathSeparator + currentPath);
        }

        try {
            Process process = builder.start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new ExecutorRuntimeStatus(false, configuredCommand, executable.toString(),
                        null, "Codex 检测超时（" + TIMEOUT_SECONDS + " 秒）", checkedAt);
            }
            String output;
            try (var input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
            if (process.exitValue() != 0) {
                return new ExecutorRuntimeStatus(false, configuredCommand, executable.toString(),
                        null,
                        truncate("Codex 无法正常执行（退出码 " + process.exitValue() + "）："
                                + nonBlank(output, "没有输出")),
                        checkedAt);
            }
            String version = firstLine(output);
            return new ExecutorRuntimeStatus(true, configuredCommand, executable.toString(),
                    version,
                    version == null
                            ? "Codex 可执行文件可用，但 --version 没有返回版本号"
                            : "Codex 可执行文件可用",
                    checkedAt);
        } catch (IOException e) {
            return new ExecutorRuntimeStatus(false, configuredCommand, executable.toString(),
                    null, truncate("无法启动 Codex 检测：" + messageOf(e)), checkedAt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecutorRuntimeStatus(false, configuredCommand, executable.toString(),
                    null, "Codex 检测被中断", checkedAt);
        }
    }

    private ExecutorRuntimeStatus unavailable(Instant checkedAt, String message) {
        return new ExecutorRuntimeStatus(false, configuredCommand, null, null, message, checkedAt);
    }

    private static String firstLine(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        int end = output.indexOf('\n');
        String line = (end < 0 ? output : output.substring(0, end)).trim();
        return line.isEmpty() ? null : truncate(line);
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_MESSAGE_CHARS) {
            return value;
        }
        return value.substring(0, MAX_MESSAGE_CHARS);
    }

    private static String messageOf(Exception error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }
}
