package com.pydlove.regent.review.infrastructure.git;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitCliCommitGatewayTest {

    @TempDir
    Path repository;

    private final GitCliCommitGateway gateway = new GitCliCommitGateway();

    @BeforeEach
    void setUp() throws IOException {
        git("init", "-q");
        git("config", "user.name", "Regent Test");
        git("config", "user.email", "regent-test@example.com");
        Files.writeString(repository.resolve("reviewed.txt"), "base\n", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve("other.txt"), "base\n", StandardCharsets.UTF_8);
        git("add", "--", "reviewed.txt", "other.txt");
        git("commit", "-q", "-m", "chore(test): init");
    }

    @Test
    void 只提交审阅文件并保留其他工作区改动() throws IOException {
        Files.writeString(repository.resolve("reviewed.txt"), "reviewed\n", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve("other.txt"), "unreviewed\n", StandardCharsets.UTF_8);

        var result = gateway.commit(repository.toString(), List.of("reviewed.txt"),
                "feat(test): 提交审阅文件");

        assertEquals("reviewed.txt", git("show", "--pretty=format:", "--name-only", "HEAD").strip());
        assertTrue(git("status", "--porcelain").contains("other.txt"));
        assertTrue(gateway.matchesCommit(repository.toString(), result.commitSha(),
                List.of("reviewed.txt")));
    }

    @Test
    void 已有暂存改动时失败关闭() throws IOException {
        Files.writeString(repository.resolve("other.txt"), "staged\n", StandardCharsets.UTF_8);
        git("add", "--", "other.txt");

        var inspection = gateway.inspect(repository.toString(), List.of("reviewed.txt"));

        assertFalse(inspection.indexClean());
        assertEquals(List.of("other.txt"), inspection.stagedPaths());
        assertThrows(DomainException.class,
                () -> gateway.commit(repository.toString(), List.of("reviewed.txt"),
                        "feat(test): 不应提交"));
    }

    @Test
    void 提交后审阅文件再次变化时复核失败() throws IOException {
        Files.writeString(repository.resolve("reviewed.txt"), "reviewed\n", StandardCharsets.UTF_8);
        var result = gateway.commit(repository.toString(), List.of("reviewed.txt"),
                "feat(test): 提交审阅文件");

        Files.writeString(repository.resolve("reviewed.txt"), "changed after commit\n",
                StandardCharsets.UTF_8);

        assertFalse(gateway.matchesCommit(repository.toString(), result.commitSha(),
                List.of("reviewed.txt")));
    }

    private String git(String... args) {
        List<String> command = new java.util.ArrayList<>(List.of(
                "git", "-C", repository.toString()));
        command.addAll(List.of(args));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            if (exit != 0) {
                throw new AssertionError("Git 命令失败：" + String.join(" ", args) + "\n" + output);
            }
            return output;
        } catch (IOException exception) {
            throw new AssertionError("Git 命令无法执行", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Git 命令被中断", exception);
        }
    }
}
