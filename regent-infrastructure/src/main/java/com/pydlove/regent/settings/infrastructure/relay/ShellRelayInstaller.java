package com.pydlove.regent.settings.infrastructure.relay;

import com.pydlove.regent.settings.domain.port.RelayInstaller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 调用项目现有远程部署脚本安装中继。
 *
 * <p>密码通过环境变量交给 SSH 的 askpass 辅助进程，不出现在命令行、日志或设置表中。
 */
public class ShellRelayInstaller implements RelayInstaller {

    private static final int MAX_MESSAGE_LENGTH = 6000;
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\u001B\\[[;\\d]*m");

    private final Path script;
    private final Duration timeout;

    public ShellRelayInstaller(Path script, Duration timeout) {
        this.script = script.toAbsolutePath().normalize();
        this.timeout = timeout;
    }

    @Override
    public InstallResult install(InstallRequest request) {
        if (!Files.isRegularFile(script)) {
            return failure(request.installDirectory(), "部署脚本不存在：" + script);
        }

        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("regent-relay-install-", ".log");
            ProcessBuilder builder = new ProcessBuilder(command())
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile());
            configureEnvironment(builder.environment(), request);

            Process process = builder.start();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                return failure(
                        request.installDirectory(),
                        "安装超时（" + timeout.toMinutes() + " 分钟），请检查服务器和部署日志"
                );
            }

            String output = readOutput(outputFile);
            if (process.exitValue() != 0) {
                return failure(
                        request.installDirectory(),
                        output.isBlank() ? "安装脚本执行失败" : "安装失败：\n" + output
                );
            }
            return new InstallResult(
                    true,
                    request.installDirectory(),
                    "中继安装完成，安装目录：" + request.installDirectory()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(request.installDirectory(), "安装被中断");
        } catch (IOException exception) {
            return failure(request.installDirectory(), "无法启动安装脚本：" + exception.getMessage());
        } finally {
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ignored) {
                    // 临时日志清理失败不影响安装结果，系统临时目录会自行回收。
                }
            }
        }
    }

    private List<String> command() {
        return new ArrayList<>(List.of("bash", script.toString()));
    }

    private static void configureEnvironment(
            java.util.Map<String, String> environment,
            InstallRequest request
    ) {
        environment.put("REGENT_SERVER_IP", request.serverIp());
        environment.put("REGENT_SERVER_USER", request.sshUsername());
        environment.put("REGENT_REMOTE_BASE", request.installDirectory());
        if (request.password() != null && !request.password().isEmpty()) {
            environment.put("REGENT_SERVER_PASSWORD", request.password());
        } else {
            environment.remove("REGENT_SERVER_PASSWORD");
        }
    }

    private static String readOutput(Path outputFile) throws IOException {
        String text = Files.readString(outputFile, StandardCharsets.UTF_8);
        text = ANSI_ESCAPE.matcher(text).replaceAll("").trim();
        if (text.length() <= MAX_MESSAGE_LENGTH) {
            return text;
        }
        return text.substring(text.length() - MAX_MESSAGE_LENGTH);
    }

    private static InstallResult failure(String installDirectory, String message) {
        return new InstallResult(false, installDirectory, message);
    }
}
