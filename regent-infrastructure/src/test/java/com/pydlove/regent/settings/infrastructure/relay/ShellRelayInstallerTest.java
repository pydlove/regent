package com.pydlove.regent.settings.infrastructure.relay;

import com.pydlove.regent.settings.domain.port.RelayInstaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 远程安装适配器对无 TTY 子进程、环境变量和失败输出的处理。 */
class ShellRelayInstallerTest {

    @TempDir
    Path tempDir;

    @Test
    void 把连接信息经环境变量传给部署脚本() throws IOException {
        Path script = script("""
                #!/usr/bin/env bash
                set -eu
                [ "$REGENT_SERVER_IP" = "203.0.113.10" ]
                [ "$REGENT_SERVER_USER" = "deploy" ]
                [ "$REGENT_SERVER_PASSWORD" = "temporary-secret" ]
                [ "$REGENT_REMOTE_BASE" = "/opt/regent-relay" ]
                echo installed
                """);
        ShellRelayInstaller installer = new ShellRelayInstaller(script, Duration.ofSeconds(5));

        RelayInstaller.InstallResult result = installer.install(new RelayInstaller.InstallRequest(
                "203.0.113.10",
                "deploy",
                "temporary-secret",
                "/opt/regent-relay"
        ));

        assertTrue(result.success());
        assertTrue(result.message().contains("/opt/regent-relay"));
    }

    @Test
    void 脚本失败时返回末尾日志而不是抛出异常() throws IOException {
        Path script = script("""
                #!/usr/bin/env bash
                echo "SSH 认证失败"
                exit 7
                """);
        ShellRelayInstaller installer = new ShellRelayInstaller(script, Duration.ofSeconds(5));

        RelayInstaller.InstallResult result = installer.install(new RelayInstaller.InstallRequest(
                "203.0.113.10",
                "root",
                "",
                "/opt/regent-relay"
        ));

        assertFalse(result.success());
        assertTrue(result.message().contains("SSH 认证失败"));
    }

    @Test
    void 部署脚本缺失时给出明确失败结果() {
        ShellRelayInstaller installer = new ShellRelayInstaller(
                tempDir.resolve("missing.sh"),
                Duration.ofSeconds(5)
        );

        RelayInstaller.InstallResult result = installer.install(new RelayInstaller.InstallRequest(
                "203.0.113.10",
                "root",
                "",
                "/opt/regent-relay"
        ));

        assertFalse(result.success());
        assertTrue(result.message().contains("部署脚本不存在"));
    }

    private Path script(String content) throws IOException {
        Path path = tempDir.resolve("deploy.sh");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }
}
