package com.pydlove.regent.execution.infrastructure.codex;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexCommandResolverTest {

    private Path workspace;

    @BeforeEach
    void 准备环境() throws IOException {
        workspace = Files.createTempDirectory("regent-codex-resolver-");
    }

    @AfterEach
    void 清理() throws IOException {
        try (var paths = Files.walk(workspace)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    @DisplayName("显式绝对路径存在且可执行时原样使用")
    void 绝对路径() throws IOException {
        Path codex = 可执行文件(workspace.resolve("custom/bin/codex"));

        assertEquals(codex, CodexCommandResolver.resolve(codex.toString(), Map.of(), workspace).orElseThrow());
    }

    @Test
    @DisplayName("绝对路径不存在时不回退到别的 codex 版本")
    void 绝对路径失效不猜版本() throws IOException {
        可执行文件(workspace.resolve(".nvm/versions/node/v24.13.0/bin/codex"));

        assertTrue(CodexCommandResolver.resolve("/nonexistent/codex", Map.of(), workspace).isEmpty());
    }

    @Test
    @DisplayName("裸命令优先从 PATH 中查找")
    void 从PATH查找() throws IOException {
        Path bin = workspace.resolve("path-bin");
        Path codex = 可执行文件(bin.resolve("codex"));

        assertEquals(codex, CodexCommandResolver
                .resolve("codex", Map.of("PATH", bin.toString()), workspace).orElseThrow());
    }

    @Test
    @DisplayName("launchd 只给系统 PATH 时仍能从 NVM 找到 codex")
    void 从NVM兜底查找() throws IOException {
        Path codex = 可执行文件(workspace.resolve(".nvm/versions/node/v24.13.0/bin/codex"));

        assertEquals(codex, CodexCommandResolver
                .resolve("codex", Map.of("PATH", "/usr/bin:/bin"), workspace).orElseThrow());
    }

    @Test
    @DisplayName("不可执行文件会被跳过")
    void 跳过不可执行文件() throws IOException {
        Path bin = workspace.resolve("path-bin");
        Path codex = bin.resolve("codex-missing");
        Files.createDirectories(bin);
        Files.writeString(codex, "#!/bin/bash\n");
        Files.setPosixFilePermissions(codex, PosixFilePermissions.fromString("rw-r--r--"));

        assertTrue(CodexCommandResolver
                .resolve("codex-missing", Map.of("PATH", bin.toString()), workspace).isEmpty());
    }

    @Test
    @DisplayName("完全找不到时返回空，由调用方保留原始配置报错")
    void 找不到返回空() {
        assertTrue(CodexCommandResolver
                .resolve("codex-missing", Map.of("PATH", "/usr/bin:/bin"), workspace).isEmpty());
    }

    private static Path 可执行文件(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "#!/bin/bash\n");
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwxr-xr-x"));
        return path.toAbsolutePath().normalize();
    }
}
