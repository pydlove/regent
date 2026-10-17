package com.pydlove.regent.execution.infrastructure.git;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** 用真实 Git 验证 reference-transaction hook：新分支被拒绝，任务分支提交仍可用。 */
class GitRefGuardTest {

    private Path repo;
    private Path runs;
    private Path guard;

    @BeforeEach
    void 准备仓库() throws IOException {
        Assumptions.assumeTrue(命令成功(null, "git", "--version"), "本机没有 Git，跳过引用守卫测试");
        repo = Files.createTempDirectory("regent-guard-repo-");
        runs = Files.createTempDirectory("regent-guard-runs-");
        assertEquals(0, run(null, "git", "init").exitCode());
        assertEquals(0, run(null, "git", "config", "user.email", "regent@example.test").exitCode());
        assertEquals(0, run(null, "git", "config", "user.name", "Regent Test").exitCode());
        Files.writeString(repo.resolve("README.md"), "initial\n", StandardCharsets.UTF_8);
        assertEquals(0, run(null, "git", "add", "README.md").exitCode());
        assertEquals(0, run(null, "git", "commit", "-m", "initial").exitCode());

        String branch = run(null, "git", "rev-parse", "--abbrev-ref", "HEAD").output().strip();
        guard = GitRefGuard.install(runs, branch);
    }

    @AfterEach
    void 清理() throws IOException {
        GitRefGuard.cleanup(guard);
        deleteRecursively(repo);
        deleteRecursively(runs);
    }

    @Test
    @DisplayName("执行器新建其他分支时被 Git 拒绝")
    void 拒绝新建分支() {
        CommandResult result = run(GitRefGuard.environment(guard), "git", "branch", "rogue");

        assertNotEquals(0, result.exitCode());
        assertFalse(Files.exists(repo.resolve(".git/refs/heads/rogue")));
    }

    @Test
    @DisplayName("任务分支上的正常提交不受影响")
    void 允许任务分支提交() throws IOException {
        Files.writeString(repo.resolve("README.md"), "changed\n", StandardCharsets.UTF_8);
        assertEquals(0, run(GitRefGuard.environment(guard), "git", "add", "README.md").exitCode());

        CommandResult result = run(GitRefGuard.environment(guard), "git", "commit", "-m", "task change");

        assertEquals(0, result.exitCode(), result.output());
    }

    private CommandResult run(Map<String, String> extraEnvironment, String... command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(repo.toFile());
        builder.redirectErrorStream(true);
        if (extraEnvironment != null) {
            builder.environment().putAll(new HashMap<>(extraEnvironment));
        }
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.waitFor(), output);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private boolean 命令成功(Map<String, String> extraEnvironment, String... command) {
        if (repo == null) {
            try {
                Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
                process.getInputStream().readAllBytes();
                return process.waitFor() == 0;
            } catch (IOException e) {
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return run(extraEnvironment, command).exitCode() == 0;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响断言
                }
            });
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}
