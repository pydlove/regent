package com.pydlove.regent.deployment.infrastructure.process;

import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionMode;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionRequest;
import com.pydlove.regent.deployment.domain.model.DeploymentExecutionResult;
import com.pydlove.regent.deployment.domain.model.DeploymentRunStatus;
import com.pydlove.regent.deployment.domain.port.DeploymentExecutor;
import com.pydlove.regent.deployment.domain.port.DeploymentLogSink;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 通过本机进程执行部署脚本。
 *
 * <p>远端模式把脚本从 stdin 传给服务器的 {@code bash -s}；项目发布模式则在项目根目录执行
 * 指定的 deploy.sh，由该脚本完成构建、上传和重启。
 *
 * <p>远端脚本不会先上传到服务器，也不在命令行里拼接脚本内容。
 * 密码认证借助 SSH 的 askpass 辅助进程：密码放在环境变量里，不出现在 argv、日志或数据库。
 * 密钥认证直接使用本机 ssh-agent / 默认私钥，并设置 BatchMode，避免交互式输入卡住后台线程。
 *
 * <p>远端脚本通过固定的 {@code REGENT_DEPLOY_*} 环境变量读取部署上下文。项目发布脚本通过
 * argv 接收连接参数，密码仅通过 {@code REGENT_SERVER_PASSWORD} 环境变量传递。两种模式都不会
 * 把密码写进命令行或日志。
 */
public class ProcessDeploymentExecutor implements DeploymentExecutor {

    private static final String PASSWORD_ENV = "REGENT_DEPLOY_PASSWORD";

    private final String sshCommand;
    private final Duration timeout;

    public ProcessDeploymentExecutor(String sshCommand, Duration timeout) {
        this.sshCommand = sshCommand;
        this.timeout = timeout;
    }

    @Override
    public DeploymentExecutionResult execute(DeploymentExecutionRequest request, DeploymentLogSink sink) {
        Path askpass = null;
        Process process = null;
        boolean projectScript = request.executionMode() == DeploymentExecutionMode.PROJECT_SCRIPT;
        String launchTarget = projectScript ? "项目发布脚本" : "SSH";
        try {
            Path script = projectScript ? resolveProjectScript(request) : null;
            List<String> command = projectScript ? projectScriptCommand(request, script) : sshCommand(request);
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (projectScript) {
                builder.directory(Path.of(request.projectDirectory()).toFile());
                configureProjectScriptEnvironment(builder.environment(), request);
                sink.append("[平台] 本机执行项目脚本：" + request.projectScriptPath() + "\n");
                sink.append("[平台] 本机工具 " + toolSummary(builder.environment()) + "\n");
            } else {
                configureRemoteEnvironment(builder.environment(), request);
                if (request.authType() == DeploymentAuthType.PASSWORD) {
                    askpass = createAskpass();
                    builder.environment().put("SSH_ASKPASS", askpass.toString());
                    builder.environment().put("SSH_ASKPASS_REQUIRE", "force");
                    builder.environment().put("DISPLAY", ":0");
                }
            }

            process = builder.start();
            OutputPump pump = new OutputPump(process.getInputStream(), sink);
            Thread reader = new Thread(pump, "regent-deploy-output");
            reader.setDaemon(true);
            reader.start();

            if (!projectScript) {
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(request.scriptContent().getBytes(StandardCharsets.UTF_8));
                    stdin.write('\n');
                    stdin.flush();
                } catch (IOException e) {
                    // 连接提前失败时写 stdin 会报 broken pipe，真正的错误由 stderr 输出承担
                    sink.append("[平台] 脚本写入连接失败：" + messageOf(e) + "\n");
                }
            } else {
                process.getOutputStream().close();
            }

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                reader.join(2_000);
                sink.append("[平台] 部署超时（" + timeoutLabel() + "），进程已终止\n");
                return new DeploymentExecutionResult(
                        DeploymentRunStatus.TIMEOUT, null, "部署超时（" + timeoutLabel() + "）");
            }

            reader.join(2_000);
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return new DeploymentExecutionResult(
                        DeploymentRunStatus.SUCCESS, 0, "部署完成（退出码 0）");
            }
            return new DeploymentExecutionResult(
                    DeploymentRunStatus.FAILED, exitCode, "部署失败（退出码 " + exitCode + "）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            sink.append("[平台] 部署被中断\n");
            return new DeploymentExecutionResult(
                    DeploymentRunStatus.INTERRUPTED, null, "部署执行被中断");
        } catch (IOException e) {
            sink.append("[平台] 无法启动" + launchTarget + "：" + messageOf(e) + "\n");
            return new DeploymentExecutionResult(
                    DeploymentRunStatus.FAILED, null, "无法启动" + launchTarget + "：" + messageOf(e));
        } finally {
            if (askpass != null) {
                try {
                    Files.deleteIfExists(askpass);
                } catch (IOException ignored) {
                    // askpass 只是临时辅助文件，清理失败不改变执行结论
                }
            }
        }
    }

    private List<String> sshCommand(DeploymentExecutionRequest request) {
        List<String> command = new ArrayList<>();
        command.add(sshCommand);
        command.add("-T");
        command.add("-p");
        command.add(Integer.toString(request.port()));
        command.add("-o");
        command.add("ConnectTimeout=10");
        command.add("-o");
        command.add("ServerAliveInterval=15");
        command.add("-o");
        command.add("ServerAliveCountMax=3");
        command.add("-o");
        command.add("StrictHostKeyChecking=accept-new");
        if (request.authType() == DeploymentAuthType.PASSWORD) {
            command.add("-o");
            command.add("PubkeyAuthentication=no");
            command.add("-o");
            command.add("PreferredAuthentications=password,keyboard-interactive");
            command.add("-o");
            command.add("NumberOfPasswordPrompts=1");
        } else {
            command.add("-o");
            command.add("BatchMode=yes");
            command.add("-o");
            command.add("PreferredAuthentications=publickey");
        }
        command.add(request.username() + "@" + request.host());
        command.add(remoteCommand(request));
        return command;
    }

    private static List<String> projectScriptCommand(DeploymentExecutionRequest request, Path script) {
        return List.of(
                "bash",
                script.toString(),
                "-H", request.host(),
                "-P", Integer.toString(request.port()),
                "-u", request.username(),
                "-r", request.remoteDirectory());
    }

    private static String remoteCommand(DeploymentExecutionRequest request) {
        StringBuilder command = new StringBuilder();
        command.append("cd ").append(shellDirectory(request.remoteDirectory())).append(" && env");
        appendEnvironment(command, "REGENT_DEPLOY_RUN_ID", Long.toString(request.runId().value()));
        appendEnvironment(command, "REGENT_DEPLOY_PROJECT_ID", Long.toString(request.projectId().value()));
        appendEnvironment(command, "REGENT_DEPLOY_HOST", request.host());
        appendEnvironment(command, "REGENT_DEPLOY_PORT", Integer.toString(request.port()));
        appendEnvironment(command, "REGENT_DEPLOY_USER", request.username());
        appendEnvironment(command, "REGENT_DEPLOY_AUTH_TYPE", request.authType().name());
        command.append(" REGENT_DEPLOY_DIRECTORY=\"$(pwd -P)\"");
        appendEnvironment(command, "REGENT_DEPLOY_SCRIPT", request.scriptFilename());
        command.append(" bash -s");
        return command.toString();
    }

    private static void appendEnvironment(StringBuilder command, String name, String value) {
        command.append(' ').append(name).append('=').append(shellQuote(value));
    }

    private static String shellDirectory(String directory) {
        if (directory.equals("~") || directory.startsWith("~/")) {
            return directory;
        }
        return shellQuote(directory);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static void configureRemoteEnvironment(Map<String, String> environment,
                                                   DeploymentExecutionRequest request) {
        environment.put(PASSWORD_ENV, request.password() == null ? "" : request.password());
    }

    private static void configureProjectScriptEnvironment(Map<String, String> environment,
                                                          DeploymentExecutionRequest request) {
        environment.put("PATH", localProcessPath(environment.get("PATH")));
        environment.put("REGENT_DEPLOY_RUN_ID", Long.toString(request.runId().value()));
        environment.put("REGENT_DEPLOY_PROJECT_ID", Long.toString(request.projectId().value()));
        environment.put("REGENT_DEPLOY_HOST", request.host());
        environment.put("REGENT_DEPLOY_PORT", Integer.toString(request.port()));
        environment.put("REGENT_DEPLOY_USER", request.username());
        environment.put("REGENT_DEPLOY_AUTH_TYPE", request.authType().name());
        environment.put("REGENT_DEPLOY_DIRECTORY", request.projectDirectory());
        environment.put("REGENT_DEPLOY_SCRIPT", request.scriptFilename());
        environment.put("REGENT_REMOTE_BASE", request.remoteDirectory());

        if (request.authType() == DeploymentAuthType.PASSWORD) {
            environment.put("REGENT_SERVER_PASSWORD", request.password());
        } else {
            environment.remove("REGENT_SERVER_PASSWORD");
            environment.remove("REGENT_SSH_PASSWORD");
            environment.remove("SSHPASS");
            environment.remove("SSH_ASKPASS");
            environment.remove("SSH_ASKPASS_REQUIRE");
        }
    }

    /**
     * launchd 启动的后端只拿到系统最小 PATH，而部署脚本通常依赖用户安装的 Maven、NVM 和 Homebrew。
     * 这里按常见版本管理器目录补齐，并保持最后才回退到系统路径。
     */
    static String localProcessPath(String inheritedPath) {
        Set<String> directories = new LinkedHashSet<>();
        if (inheritedPath != null && !inheritedPath.isBlank()) {
            for (String entry : inheritedPath.split(":")) {
                if (!entry.isBlank()) {
                    directories.add(entry);
                }
            }
        }

        Path home = Path.of(System.getProperty("user.home", ""));
        addVersionedBinDirectories(directories, home.resolve(".nvm/versions/node"));
        addVersionedBinDirectories(directories, home.resolve("software/maven"));
        addDirectory(directories, home.resolve(".sdkman/candidates/maven/current/bin"));
        addDirectory(directories, home.resolve(".sdkman/candidates/java/current/bin"));
        addDirectory(directories, home.resolve(".asdf/shims"));
        addDirectory(directories, home.resolve(".local/share/mise/shims"));
        addDirectory(directories, home.resolve(".volta/bin"));
        addDirectory(directories, home.resolve("bin"));

        addDirectory(directories, Path.of("/opt/homebrew/bin"));
        addDirectory(directories, Path.of("/opt/homebrew/sbin"));
        addDirectory(directories, Path.of("/usr/local/bin"));
        addDirectory(directories, Path.of("/usr/local/sbin"));
        addDirectory(directories, Path.of("/usr/bin"));
        addDirectory(directories, Path.of("/bin"));
        addDirectory(directories, Path.of("/usr/sbin"));
        addDirectory(directories, Path.of("/sbin"));
        return String.join(":", directories);
    }

    private static void addVersionedBinDirectories(Set<String> directories, Path versionsDirectory) {
        if (!Files.isDirectory(versionsDirectory)) {
            return;
        }
        try (Stream<Path> versions = Files.list(versionsDirectory)) {
            versions.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .map(path -> path.resolve("bin"))
                    .filter(Files::isDirectory)
                    .forEach(path -> addDirectory(directories, path));
        } catch (IOException ignored) {
            // 版本目录不可读时继续使用已有的 PATH，实际命令是否可用会在脚本输出里体现
        }
    }

    private static void addDirectory(Set<String> directories, Path directory) {
        if (Files.isDirectory(directory)) {
            directories.add(directory.toString());
        }
    }

    private static String toolSummary(Map<String, String> environment) {
        String path = environment.getOrDefault("PATH", "");
        List<String> tools = new ArrayList<>();
        for (String name : List.of("mvn", "npm", "node", "rsync", "ssh")) {
            tools.add(name + "=" + findExecutable(name, path).map(Path::toString).orElse("未找到"));
        }
        return String.join("，", tools);
    }

    private static Optional<Path> findExecutable(String name, String path) {
        for (String entry : path.split(":")) {
            if (entry.isBlank()) {
                continue;
            }
            Path candidate = Path.of(entry, name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Path resolveProjectScript(DeploymentExecutionRequest request) throws IOException {
        try {
            Path root = Path.of(request.projectDirectory()).toAbsolutePath().normalize();
            Path script = root.resolve(request.projectScriptPath()).normalize();
            if (!script.startsWith(root)) {
                throw new IOException("项目脚本路径越出项目目录：" + request.projectScriptPath());
            }
            Path realRoot;
            try {
                realRoot = root.toRealPath();
            } catch (IOException e) {
                throw new IOException("项目目录不存在或不可访问：" + root, e);
            }
            if (!Files.isRegularFile(script)) {
                throw new IOException("项目脚本不存在或不是普通文件：" + request.projectScriptPath());
            }
            Path realScript = script.toRealPath();
            if (!realScript.startsWith(realRoot)) {
                throw new IOException("项目脚本通过链接越出项目目录：" + request.projectScriptPath());
            }
            byte[] uploaded = request.scriptContent().getBytes(StandardCharsets.UTF_8);
            if (Files.size(realScript) != uploaded.length) {
                throw new IOException(
                        "项目脚本内容与上传脚本不一致，请重新上传：" + request.projectScriptPath());
            }
            byte[] actual = Files.readAllBytes(realScript);
            if (!Arrays.equals(actual, uploaded)) {
                throw new IOException(
                        "项目脚本内容与上传脚本不一致，请重新上传：" + request.projectScriptPath());
            }
            return realScript;
        } catch (InvalidPathException e) {
            throw new IOException("项目目录或脚本路径不合法", e);
        }
    }

    private static Path createAskpass() throws IOException {
        Path path = Files.createTempFile("regent-deploy-askpass-", ".sh");
        Files.writeString(path,
                "#!/bin/sh\nprintf '%s\\n' \"$" + PASSWORD_ENV + "\"\n",
                StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // 非 POSIX 文件系统仍可通过进程环境完成认证；本项目正式运行在 macOS/Linux
        }
        return path;
    }

    private String timeoutLabel() {
        if (timeout.toMinutes() >= 1) {
            return timeout.toMinutes() + " 分钟";
        }
        return timeout.getSeconds() + " 秒";
    }

    private static String messageOf(IOException e) {
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }

    private static final class OutputPump implements Runnable {

        private final InputStream input;
        private final DeploymentLogSink sink;

        private OutputPump(InputStream input, DeploymentLogSink sink) {
            this.input = input;
            this.sink = sink;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[8_192];
            try (input) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    try {
                        sink.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    } catch (RuntimeException ignored) {
                        // 日志落盘失败不应反过来杀死 SSH 进程，退出码仍然是最重要的事实
                    }
                }
            } catch (IOException ignored) {
                // 进程结束时管道关闭是正常路径
            }
        }
    }
}
