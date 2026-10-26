package com.pydlove.regent.project.infrastructure.editor;

import com.pydlove.regent.project.domain.model.CodeEditorDescriptor;
import com.pydlove.regent.project.domain.model.CodeEditorId;
import com.pydlove.regent.project.domain.port.CodeEditorGateway;
import com.pydlove.regent.shared.domain.DomainException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 检测并调用本机白名单编辑器。
 *
 * <p>优先使用已安装的 CLI，以便把编辑器定位到具体文件和行；没有 CLI 但安装了 macOS 应用时，
 * 使用 {@code open -a} 打开项目目录。前端只能提交枚举 ID，不能提交可执行命令。
 */
public class LocalCodeEditorGateway implements CodeEditorGateway {

    private static final long LAUNCH_TIMEOUT_SECONDS = 8;

    private final String osName;
    private final Path home;

    public LocalCodeEditorGateway() {
        this(System.getProperty("os.name", ""), Path.of(System.getProperty("user.home", "")));
    }

    LocalCodeEditorGateway(String osName, Path home) {
        this.osName = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        this.home = home;
    }

    @Override
    public List<CodeEditorDescriptor> availableEditors() {
        List<CodeEditorDescriptor> editors = new ArrayList<>();
        inspect(CodeEditorId.VS_CODE).ifPresent(editors::add);
        inspect(CodeEditorId.CURSOR).ifPresent(editors::add);
        inspect(CodeEditorId.INTELLIJ_IDEA).ifPresent(editors::add);
        return List.copyOf(editors);
    }

    @Override
    public void open(String projectPath,
                     String relativePath,
                     Integer line,
                     CodeEditorId editorId) {
        Path root = Path.of(projectPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new DomainException("项目目录不存在：" + root);
        }
        Path file = relativePath == null
                ? null
                : root.resolve(relativePath).normalize();
        if (file != null && (!file.startsWith(root) || !Files.isRegularFile(file))) {
            throw new DomainException("代码文件不存在或越出项目目录：" + relativePath);
        }
        EditorInstallation installation = installation(editorId)
                .orElseThrow(() -> new DomainException("当前电脑未安装所选代码编辑器"));

        if (installation.cliPath() != null) {
            launchCli(installation, root, file, line);
        } else {
            launchApplication(installation, root, file);
        }
    }

    private Optional<CodeEditorDescriptor> inspect(CodeEditorId id) {
        return installation(id).map(EditorInstallation::descriptor);
    }

    private Optional<EditorInstallation> installation(CodeEditorId id) {
        List<String> executableNames = switch (id) {
            case VS_CODE -> List.of("code");
            case CURSOR -> List.of("cursor");
            case INTELLIJ_IDEA -> List.of("idea");
        };
        List<String> knownPaths = switch (id) {
            case VS_CODE -> List.of(
                    "/Applications/Visual Studio Code.app/Contents/Resources/app/bin/code",
                    home.resolve(".local/bin/code").toString());
            case CURSOR -> List.of(
                    "/Applications/Cursor.app/Contents/Resources/app/bin/cursor",
                    home.resolve(".local/bin/cursor").toString());
            case INTELLIJ_IDEA -> List.of(
                    "/Applications/IntelliJ IDEA.app/Contents/MacOS/idea",
                    "/Applications/IntelliJ IDEA CE.app/Contents/MacOS/idea",
                    home.resolve(".local/bin/idea").toString());
        };
        String cli = findExecutable(executableNames, knownPaths).orElse(null);
        String appName = cli != null ? null : switch (id) {
            case VS_CODE -> installedApp("Visual Studio Code").orElse(null);
            case CURSOR -> installedApp("Cursor").orElse(null);
            case INTELLIJ_IDEA -> installedApp("IntelliJ IDEA CE")
                    .orElse(installedApp("IntelliJ IDEA").orElse(null));
        };
        if (cli == null && appName == null) {
            return Optional.empty();
        }
        String name = switch (id) {
            case VS_CODE -> "Visual Studio Code";
            case CURSOR -> "Cursor";
            case INTELLIJ_IDEA -> "IntelliJ IDEA";
        };
        return Optional.of(new EditorInstallation(
                new CodeEditorDescriptor(id, name, cli != null),
                cli,
                appName));
    }

    private Optional<String> findExecutable(List<String> names, List<String> knownPaths) {
        List<String> pathEntries = new ArrayList<>();
        String pathValue = System.getenv().getOrDefault("PATH", "");
        for (String entry : pathValue.split(java.io.File.pathSeparator)) {
            if (!entry.isBlank()) {
                pathEntries.add(entry);
            }
        }
        for (String name : names) {
            for (String entry : pathEntries) {
                Path candidate = Path.of(entry, name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return Optional.of(candidate.toAbsolutePath().normalize().toString());
                }
            }
        }
        for (String path : knownPaths) {
            Path candidate = Path.of(path);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize().toString());
            }
        }
        return Optional.empty();
    }

    private Optional<String> installedApp(String appName) {
        if (!osName.contains("mac")) {
            return Optional.empty();
        }
        ProcessResult result = run(List.of("/usr/bin/open", "-Ra", appName), false);
        return result.exitCode() == 0 ? Optional.of(appName) : Optional.empty();
    }

    private void launchCli(EditorInstallation installation, Path root, Path file, Integer line) {
        launch(List.of(installation.cliPath(), root.toString()), true);
        if (file == null) {
            return;
        }
        List<String> args = switch (installation.descriptor().id()) {
            case INTELLIJ_IDEA -> {
                List<String> value = new ArrayList<>(List.of(
                        installation.cliPath(), "--line"));
                value.add(Integer.toString(line == null ? 1 : line));
                value.add(file.toString());
                yield value;
            }
            case VS_CODE, CURSOR -> List.of(
                    installation.cliPath(),
                    "-g",
                    file + (line == null ? "" : ":" + line));
        };
        launch(args, true);
    }

    private void launchApplication(EditorInstallation installation, Path root, Path file) {
        launch(List.of(
                "/usr/bin/open",
                "-a",
                installation.appName(),
                root.toString()), true);
        if (file != null) {
            launch(List.of(
                    "/usr/bin/open",
                    "-a",
                    installation.appName(),
                    file.toString()), true);
        }
    }

    private void launch(List<String> command, boolean requireSuccess) {
        ProcessResult result = run(command, requireSuccess);
        if (requireSuccess && result.exitCode() != 0) {
            throw new DomainException("代码编辑器启动失败：" + firstLine(result.output()));
        }
    }

    private ProcessResult run(List<String> command, boolean requireSuccess) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                // 编辑器可能已接管进程但仍保持父进程存活；超时不再等待，也不把它判为启动失败。
                return new ProcessResult(0, output);
            }
            return new ProcessResult(process.exitValue(), output);
        } catch (IOException exception) {
            if (!requireSuccess) {
                return new ProcessResult(1, exception.getMessage());
            }
            throw new DomainException("无法启动代码编辑器", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException("代码编辑器启动被中断", exception);
        }
    }

    private static String firstLine(String value) {
        String normalized = value == null ? "" : value.strip();
        int newline = normalized.indexOf('\n');
        return newline < 0 ? normalized : normalized.substring(0, newline).strip();
    }

    private record EditorInstallation(
            CodeEditorDescriptor descriptor,
            String cliPath,
            String appName) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
