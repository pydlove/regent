package com.pydlove.regent.execution.infrastructure.codex;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 把配置里的 codex 命令解析成实际可执行文件。
 *
 * <p>平台进程不一定从登录 shell 启动：launchd、systemd、IDE 调试器通常只给系统 PATH。
 * 这时让 ProcessBuilder 直接找 {@code codex} 会得到 error=2。配置成绝对路径可以绕过，
 * 但会把某台机器的 NVM 版本写进配置。这里保留命令名，启动时按常见位置兜底。
 *
 * <p>npm 版 codex 的 shebang 是 {@code /usr/bin/env node}，所以调用方还必须把可执行文件
 * 所在目录放进子进程 PATH，同目录的 node 才能被找到。
 */
final class CodexCommandResolver {

    private static final String COMMAND_NAME = "codex";
    private static final String LOGIN_SHELL_PATH_MARKER = "__REGENT_CODEX_PATH__=";
    private static final String LOGIN_SHELL_LOOKUP =
            "printf '\\n%s%s\\n' '" + LOGIN_SHELL_PATH_MARKER
                    + "' \"$(command -v -- \"$1\" 2>/dev/null || true)\"";
    private static final Path CHATGPT_RESOURCES = Path.of("/Applications/ChatGPT.app/Contents/Resources");
    private static final long LOGIN_SHELL_TIMEOUT_SECONDS = 3;

    private CodexCommandResolver() {
    }

    static Optional<Path> resolve(String configured) {
        Map<String, String> environment = System.getenv();
        return resolve(
                configured,
                environment,
                Path.of(System.getProperty("user.home")),
                defaultLoginShell(environment));
    }

    /**
     * 解析顺序：显式绝对路径 → 当前 PATH → 登录 shell → 常见安装目录。
     * 绝对路径配置找不到时直接失败，不偷偷换一个版本，避免「配置写的 A，跑的却是 B」。
     */
    static Optional<Path> resolve(String configured, Map<String, String> environment, Path home) {
        return resolve(configured, environment, home, null);
    }

    static Optional<Path> resolve(String configured,
                                  Map<String, String> environment,
                                  Path home,
                                  Path loginShell) {
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        Path configuredPath = Path.of(configured);
        if (configuredPath.isAbsolute()) {
            return executable(configuredPath);
        }
        if (configured.contains("/") || configured.contains("\\")) {
            return Optional.empty();
        }

        Set<Path> candidates = new LinkedHashSet<>();
        String pathValue = environment.getOrDefault("PATH", "");
        for (String directory : pathValue.split(File.pathSeparator)) {
            if (!directory.isBlank()) {
                candidates.add(Path.of(directory).resolve(configured));
            }
        }
        Optional<Path> fromProcessPath = firstExecutable(candidates);
        if (fromProcessPath.isPresent()) {
            return fromProcessPath;
        }
        if (COMMAND_NAME.equals(configured)) {
            resolveFromLoginShell(loginShell, configured).ifPresent(candidates::add);
            addEnvironmentCandidate(candidates, environment.get("NVM_BIN"), "", configured);
            addEnvironmentCandidate(candidates, environment.get("PNPM_HOME"), "", configured);
            addEnvironmentCandidate(candidates, environment.get("VOLTA_HOME"), "bin", configured);
            addEnvironmentCandidate(candidates, environment.get("BUN_INSTALL"), "bin", configured);
            addEnvironmentCandidate(candidates, environment.get("npm_config_prefix"), "bin", configured);
            addEnvironmentCandidate(candidates, environment.get("ASDF_DATA_DIR"), "shims", configured);
            addEnvironmentCandidate(candidates, environment.get("MISE_DATA_DIR"), "shims", configured);
            commonBins(home, environment).forEach(directory -> candidates.add(directory.resolve(configured)));
            candidates.add(CHATGPT_RESOURCES.resolve(configured));
        }
        return firstExecutable(candidates);
    }

    private static Optional<Path> firstExecutable(Set<Path> candidates) {
        return candidates.stream()
                .map(CodexCommandResolver::executable)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static void addEnvironmentCandidate(Set<Path> candidates,
                                                String directory,
                                                String childDirectory,
                                                String configured) {
        if (directory != null && !directory.isBlank()) {
            Path base = Path.of(directory);
            candidates.add(childDirectory.isBlank()
                    ? base.resolve(configured)
                    : base.resolve(childDirectory).resolve(configured));
        }
    }

    private static List<Path> commonBins(Path home, Map<String, String> environment) {
        List<Path> bins = new ArrayList<>();
        bins.add(home.resolve(".local/bin"));
        bins.add(home.resolve(".npm-global/bin"));
        bins.add(home.resolve(".npm/bin"));
        bins.add(home.resolve(".volta/bin"));
        bins.add(home.resolve(".bun/bin"));
        bins.add(home.resolve(".asdf/shims"));
        bins.add(home.resolve("Library/pnpm"));
        bins.add(home.resolve(".local/share/pnpm"));
        bins.add(home.resolve(".local/share/mise/shims"));
        bins.add(Path.of("/opt/homebrew/bin"));
        bins.add(Path.of("/usr/local/bin"));
        bins.add(Path.of("/usr/bin"));

        String appData = environment.get("APPDATA");
        if (appData != null && !appData.isBlank()) {
            bins.add(Path.of(appData).resolve("npm"));
        }
        String localAppData = environment.get("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            bins.add(Path.of(localAppData).resolve("Microsoft/WinGet/Links"));
        }

        bins.addAll(nvmBins(home));
        bins.addAll(versionedNodeBins(home.resolve(".local/share/fnm/node-versions"), "installation"));
        bins.addAll(versionedNodeBins(home.resolve(".local/share/mise/installs/node"), null));
        return bins;
    }

    private static Optional<Path> resolveFromLoginShell(Path shell, String configured) {
        if (shell == null || !Files.isExecutable(shell)) {
            return Optional.empty();
        }
        for (String mode : List.of("-lc", "-lic")) {
            Optional<Path> resolved = runLoginShellLookup(shell, mode, configured);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    /**
     * 把命令当作位置参数交给 shell，绝不拼进脚本文本。登录 shell 可能输出提示或插件日志，
     * 因此只接受输出中真实存在且可执行的绝对路径。
     */
    private static Optional<Path> runLoginShellLookup(Path shell, String mode, String configured) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    shell.toString(),
                    mode,
                    LOGIN_SHELL_LOOKUP,
                    "regent-codex-detect",
                    configured)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(LOGIN_SHELL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            String output;
            try (var input = process.getInputStream()) {
                output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String line : output.split("\\R")) {
                String value = line.strip();
                if (!value.startsWith(LOGIN_SHELL_PATH_MARKER)) continue;
                value = value.substring(LOGIN_SHELL_PATH_MARKER.length()).strip();
                if (value.isEmpty()) continue;
                Path candidate;
                try {
                    candidate = Path.of(value);
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (candidate.isAbsolute()) {
                    Optional<Path> executable = executable(candidate);
                    if (executable.isPresent()) return executable;
                }
            }
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
        return Optional.empty();
    }

    private static Path defaultLoginShell(Map<String, String> environment) {
        String configured = environment.get("SHELL");
        if (configured != null && !configured.isBlank()) {
            Path shell = Path.of(configured);
            if (shell.isAbsolute() && Files.isExecutable(shell)) return shell;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return null;
        if (os.contains("mac")) return Path.of("/bin/zsh");
        return Path.of("/bin/bash");
    }

    private static Optional<Path> executable(Path candidate) {
        Path normalized = candidate.toAbsolutePath().normalize();
        return Files.isRegularFile(normalized) && Files.isExecutable(normalized)
                ? Optional.of(normalized)
                : Optional.empty();
    }

    /** 多个 Node 版本时优先最近安装的那个，避免固定挑选某个旧版本。 */
    private static List<Path> nvmBins(Path home) {
        Path versions = home.resolve(".nvm/versions/node");
        if (!Files.isDirectory(versions)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(versions)) {
            return entries
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparingLong(CodexCommandResolver::lastModified).reversed())
                    .map(version -> version.resolve("bin"))
                    .filter(Files::isDirectory)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** fnm/mise 都按“版本目录下再放 bin”组织，只保留实际存在的版本。 */
    private static List<Path> versionedNodeBins(Path versions, String installationLayer) {
        if (!Files.isDirectory(versions)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(versions)) {
            return entries
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparingLong(CodexCommandResolver::lastModified).reversed())
                    .map(version -> installationLayer == null
                            ? version.resolve("bin")
                            : version.resolve(installationLayer).resolve("bin"))
                    .filter(Files::isDirectory)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
