package com.pydlove.regent.execution.infrastructure.codex;

import com.pydlove.regent.execution.domain.model.ExecutorInstallMethod;
import com.pydlove.regent.execution.domain.model.ExecutorInstallResult;
import com.pydlove.regent.execution.domain.model.ExecutorRuntimeStatus;
import com.pydlove.regent.execution.domain.port.ExecutorInstaller;
import com.pydlove.regent.execution.domain.port.ExecutorRuntimeProbe;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * 使用固定命令一键安装 Codex。
 *
 * <ul>
 *   <li>npm：{@code npm install -g @openai/codex}</li>
 *   <li>Homebrew：{@code brew install codex}</li>
 * </ul>
 *
 * <p>不接受用户传入命令或参数。npm 包与 Homebrew 源通常位于国际网络，安装失败时会明确提示检查网络。
 */
public class CodexInstaller implements ExecutorInstaller {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    private static final int MAX_OUTPUT_CHARS = 12_000;

    private final ExecutorRuntimeProbe probe;
    private final Path home;
    private final Map<String, String> environment;
    private final AtomicBoolean installing = new AtomicBoolean();

    public CodexInstaller(ExecutorRuntimeProbe probe) {
        this(probe, Path.of(System.getProperty("user.home")), System.getenv());
    }

    CodexInstaller(ExecutorRuntimeProbe probe, Path home, Map<String, String> environment) {
        this.probe = probe;
        this.home = home;
        this.environment = environment;
    }

    @Override
    public ExecutorInstallResult install(ExecutorInstallMethod method) {
        if (!installing.compareAndSet(false, true)) {
            ExecutorRuntimeStatus status = probe.check();
            return new ExecutorInstallResult(false, method,
                    "已有 Codex 安装任务正在进行，请等待当前任务完成", "", status);
        }
        try {
            Optional<Path> executable = method == ExecutorInstallMethod.NPM
                    ? findNpm()
                    : findBrew();
            if (executable.isEmpty()) {
                ExecutorRuntimeStatus status = probe.check();
                String tool = method == ExecutorInstallMethod.NPM ? "npm" : "Homebrew";
                String hint = method == ExecutorInstallMethod.NPM
                        ? "请先安装 Node.js 18+，或改用 Homebrew 安装"
                        : "请先安装 Homebrew，或改用 npm 安装";
                return new ExecutorInstallResult(false, method,
                        "未找到 " + tool + "。" + hint, "", status);
            }

            CommandResult result = run(executable.get(), method);
            ExecutorRuntimeStatus status = probe.check();
            if (result.exitCode() != 0) {
                return new ExecutorInstallResult(false, method,
                        "安装命令失败（退出码 " + result.exitCode()
                                + "）。请检查网络；访问 npm / GitHub 可能需要代理（魔法）",
                        result.output(), status);
            }
            if (!status.available()) {
                return new ExecutorInstallResult(false, method,
                        "安装命令已完成，但仍未检测到 Codex。请检查 PATH，"
                                + "或在 application-local.yml 中配置绝对路径",
                        result.output(), status);
            }
            return new ExecutorInstallResult(true, method,
                    "Codex 安装完成并已通过可用性检测", result.output(), status);
        } finally {
            installing.set(false);
        }
    }

    private Optional<Path> findNpm() {
        Optional<Path> fromPath = findInPath("npm");
        if (fromPath.isPresent()) {
            return fromPath;
        }
        List<Path> candidates = new ArrayList<>();
        nvmBins().forEach(directory -> candidates.add(directory.resolve("npm")));
        candidates.add(Path.of("/opt/homebrew/bin/npm"));
        candidates.add(Path.of("/usr/local/bin/npm"));
        return candidates.stream().map(this::executable).flatMap(Optional::stream).findFirst();
    }

    private Optional<Path> findBrew() {
        Optional<Path> fromPath = findInPath("brew");
        if (fromPath.isPresent()) {
            return fromPath;
        }
        return Stream.of(
                        Path.of("/opt/homebrew/bin/brew"),
                        Path.of("/usr/local/bin/brew"))
                .map(this::executable)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<Path> findInPath(String name) {
        String pathValue = environment.getOrDefault("PATH", "");
        for (String directory : pathValue.split(File.pathSeparator)) {
            if (directory.isBlank()) {
                continue;
            }
            Optional<Path> candidate = executable(Path.of(directory).resolve(name));
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private List<Path> nvmBins() {
        Path versions = home.resolve(".nvm/versions/node");
        if (!Files.isDirectory(versions)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(versions)) {
            return entries
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparingLong(CodexInstaller::lastModified).reversed())
                    .map(version -> version.resolve("bin"))
                    .filter(Files::isDirectory)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private Optional<Path> executable(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        return Files.isRegularFile(normalized) && Files.isExecutable(normalized)
                ? Optional.of(normalized)
                : Optional.empty();
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static CommandResult run(Path executable, ExecutorInstallMethod method) {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        if (method == ExecutorInstallMethod.NPM) {
            command.add("install");
            command.add("-g");
            command.add("@openai/codex");
        } else {
            command.add("install");
            command.add("codex");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Path binDirectory = executable.getParent();
        if (binDirectory != null) {
            String currentPath = builder.environment().getOrDefault("PATH", "");
            builder.environment().put("PATH",
                    currentPath.isBlank()
                            ? binDirectory.toString()
                            : binDirectory + File.pathSeparator + currentPath);
        }
        builder.environment().put("CI", "1");
        try {
            Process process = builder.start();
            CompletableFuture<String> output = CompletableFuture.supplyAsync(
                    () -> readOutput(process));
            if (!process.waitFor(TIMEOUT.toMinutes(), TimeUnit.MINUTES)) {
                process.destroyForcibly();
                return new CommandResult(-1,
                        awaitOutput(output) + "\n安装超过 " + TIMEOUT.toMinutes() + " 分钟，已终止");
            }
            return new CommandResult(process.exitValue(), awaitOutput(output));
        } catch (IOException e) {
            return new CommandResult(-1, "无法启动安装命令：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "安装被中断");
        }
    }

    private static String readOutput(Process process) {
        try (var input = process.getInputStream();
             var output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            return truncate(output.toString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return "读取安装输出失败：" + e.getMessage();
        }
    }

    private static String awaitOutput(CompletableFuture<String> output) {
        try {
            return output.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            output.cancel(true);
            return "读取安装输出超时";
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_OUTPUT_CHARS) {
            return value == null ? "" : value;
        }
        return value.substring(value.length() - MAX_OUTPUT_CHARS);
    }

    private record CommandResult(int exitCode, String output) {
    }
}
