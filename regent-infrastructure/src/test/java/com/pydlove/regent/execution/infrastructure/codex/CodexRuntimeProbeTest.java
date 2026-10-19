package com.pydlove.regent.execution.infrastructure.codex;

import com.pydlove.regent.execution.domain.model.ExecutorRuntimeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexRuntimeProbeTest {

    @Test
    @DisplayName("找到可执行文件并成功返回版本时判定可用")
    void detectsInstalledCodex(@TempDir Path directory) throws IOException {
        Path codex = script(directory.resolve("bin/codex"),
                "#!/bin/bash\nprintf 'codex-cli 9.9.9\\n'\n");

        ExecutorRuntimeStatus status = new CodexRuntimeProbe(codex.toString()).check();

        assertTrue(status.available());
        assertEquals(codex.toAbsolutePath().normalize().toString(), status.executablePath());
        assertEquals("codex-cli 9.9.9", status.version());
    }

    @Test
    @DisplayName("配置路径不存在时明确报告未找到")
    void reportsMissingCodex(@TempDir Path directory) {
        ExecutorRuntimeStatus status = new CodexRuntimeProbe(
                directory.resolve("missing/codex").toString()).check();

        assertFalse(status.available());
        assertTrue(status.message().contains("未找到"));
    }

    @Test
    @DisplayName("文件存在但版本命令失败时报告不可用")
    void reportsBrokenExecutable(@TempDir Path directory) throws IOException {
        Path codex = script(directory.resolve("bin/codex"),
                "#!/bin/bash\necho 'node: command not found' >&2\nexit 7\n");

        ExecutorRuntimeStatus status = new CodexRuntimeProbe(codex.toString()).check();

        assertFalse(status.available());
        assertTrue(status.message().contains("退出码 7"), status.message());
        assertTrue(status.message().contains("node: command not found"), status.message());
    }

    private static Path script(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path.toAbsolutePath().normalize();
    }
}
