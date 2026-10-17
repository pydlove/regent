package com.pydlove.regent.execution.infrastructure.codex;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final Path CHATGPT_RESOURCES = Path.of("/Applications/ChatGPT.app/Contents/Resources");

    private CodexCommandResolver() {
    }

    static Optional<Path> resolve(String configured) {
        return resolve(configured, System.getenv(), Path.of(System.getProperty("user.home")));
    }

    /**
     * 解析顺序：显式绝对路径 → 当前 PATH → 常见安装目录。
     * 绝对路径配置找不到时直接失败，不偷偷换一个版本，避免「配置写的 A，跑的却是 B」。
     */
    static Optional<Path> resolve(String configured, Map<String, String> environment, Path home) {
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

        List<Path> candidates = new ArrayList<>();
        String pathValue = environment.getOrDefault("PATH", "");
        for (String directory : pathValue.split(File.pathSeparator)) {
            if (!directory.isBlank()) {
                candidates.add(Path.of(directory).resolve(configured));
            }
        }
        if (COMMAND_NAME.equals(configured)) {
            candidates.add(home.resolve(".local/bin").resolve(configured));
            candidates.add(Path.of("/opt/homebrew/bin").resolve(configured));
            candidates.add(Path.of("/usr/local/bin").resolve(configured));
            nvmBins(home).forEach(directory -> candidates.add(directory.resolve(configured)));
            candidates.add(CHATGPT_RESOURCES.resolve(configured));
        }
        return candidates.stream()
                .map(CodexCommandResolver::executable)
                .flatMap(Optional::stream)
                .findFirst();
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

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }
}
