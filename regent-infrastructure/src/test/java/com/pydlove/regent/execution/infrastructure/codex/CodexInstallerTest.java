package com.pydlove.regent.execution.infrastructure.codex;

import com.pydlove.regent.execution.domain.model.ExecutorInstallMethod;
import com.pydlove.regent.execution.domain.model.ExecutorInstallResult;
import com.pydlove.regent.execution.domain.model.ExecutorRuntimeStatus;
import com.pydlove.regent.execution.domain.port.ExecutorRuntimeProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexInstallerTest {

    @Test
    @DisplayName("NVM 环境存在 npm 时执行固定安装命令，并在安装后重新检测")
    void installsWithNpm(@TempDir Path home) throws IOException {
        script(home.resolve(".nvm/versions/node/v24.13.0/bin/npm"),
                "#!/bin/bash\nprintf 'installed @openai/codex\\n'\nexit 0\n");
        CodexInstaller installer = new CodexInstaller(availableProbe(), home, Map.of("PATH", ""));

        ExecutorInstallResult result = installer.install(ExecutorInstallMethod.NPM);

        assertTrue(result.success(), result.message());
        assertTrue(result.output().contains("installed @openai/codex"), result.output());
        assertEquals("Codex 安装完成并已通过可用性检测", result.message());
    }

    @Test
    @DisplayName("没有 npm 和 Homebrew 时明确返回可执行的替代建议")
    void reportsMissingInstaller(@TempDir Path home) {
        CodexInstaller installer = new CodexInstaller(availableProbe(), home, Map.of("PATH", ""));

        ExecutorInstallResult result = installer.install(ExecutorInstallMethod.NPM);

        assertFalse(result.success());
        assertTrue(result.message().contains("Node.js 18+"), result.message());
    }

    @Test
    @DisplayName("安装命令失败时保留输出并提示检查国际网络")
    void reportsNetworkHint(@TempDir Path home) throws IOException {
        script(home.resolve(".nvm/versions/node/v24.13.0/bin/npm"),
                "#!/bin/bash\necho 'getaddrinfo ENOTFOUND registry.npmjs.org' >&2\nexit 1\n");
        CodexInstaller installer = new CodexInstaller(availableProbe(), home, Map.of("PATH", ""));

        ExecutorInstallResult result = installer.install(ExecutorInstallMethod.NPM);

        assertFalse(result.success());
        assertTrue(result.message().contains("网络"), result.message());
        assertTrue(result.output().contains("registry.npmjs.org"), result.output());
    }

    private static ExecutorRuntimeProbe availableProbe() {
        return () -> new ExecutorRuntimeStatus(
                true,
                "codex",
                "/tmp/codex",
                "codex-cli test",
                "Codex 可执行文件可用",
                Instant.parse("2026-10-19T00:00:00Z"));
    }

    private static Path script(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path;
    }
}
