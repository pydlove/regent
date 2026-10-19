package com.pydlove.regent.deployment.infrastructure.process;

import com.pydlove.regent.deployment.domain.model.DeploymentAuthType;
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
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 通过本机 OpenSSH 执行部署脚本。
 *
 * <p>脚本从 stdin 传给远程的 {@code bash -s}，不先上传到服务器，也不在命令行里拼接脚本内容。
 * 密码认证借助 SSH 的 askpass 辅助进程：密码放在环境变量里，不出现在 argv、日志或数据库。
 * 密钥认证直接使用本机 ssh-agent / 默认私钥，并设置 BatchMode，避免交互式输入卡住后台线程。
 *
 * <p>远端脚本通过固定的 {@code REGENT_DEPLOY_*} 环境变量读取部署上下文。SSH 密码只用于建立
 * 连接，绝不传给远端脚本；脚本需要业务凭据时应从服务器的密钥管理或受限配置中读取。
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
        try {
            List<String> command = command(request);
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            configureEnvironment(builder.environment(), request);
            if (request.authType() == DeploymentAuthType.PASSWORD) {
                askpass = createAskpass();
                builder.environment().put("SSH_ASKPASS", askpass.toString());
                builder.environment().put("SSH_ASKPASS_REQUIRE", "force");
                builder.environment().put("DISPLAY", ":0");
            }

            process = builder.start();
            OutputPump pump = new OutputPump(process.getInputStream(), sink);
            Thread reader = new Thread(pump, "regent-deploy-output");
            reader.setDaemon(true);
            reader.start();

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(request.scriptContent().getBytes(StandardCharsets.UTF_8));
                stdin.write('\n');
                stdin.flush();
            } catch (IOException e) {
                // 连接提前失败时写 stdin 会报 broken pipe，真正的错误由 stderr 输出承担
                sink.append("[平台] 脚本写入连接失败：" + messageOf(e) + "\n");
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
            sink.append("[平台] 无法启动 SSH：" + messageOf(e) + "\n");
            return new DeploymentExecutionResult(
                    DeploymentRunStatus.FAILED, null, "无法启动 SSH：" + messageOf(e));
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

    private List<String> command(DeploymentExecutionRequest request) {
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

    private static void configureEnvironment(Map<String, String> environment,
                                              DeploymentExecutionRequest request) {
        environment.put(PASSWORD_ENV, request.password() == null ? "" : request.password());
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
