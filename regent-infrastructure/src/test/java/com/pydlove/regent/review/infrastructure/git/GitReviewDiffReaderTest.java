package com.pydlove.regent.review.infrastructure.git;

import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffFile;
import com.pydlove.regent.review.domain.model.ReviewDiffRequest;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitReviewDiffReaderTest {

    @TempDir
    Path temp;

    private final GitReviewDiffReader reader = new GitReviewDiffReader();

    @Test
    void 读取已跟踪和未跟踪变更并生成稳定哈希() throws Exception {
        Path repo = gitRepository();
        String base = git(repo, "rev-parse", "HEAD").strip();
        Files.writeString(repo.resolve("Task.java"), "class Task { int value = 2; }\n");
        Files.writeString(repo.resolve("NewTest.java"), "class NewTest {}\n");

        ReviewDiff first = reader.read(new ReviewDiffRequest(repo.toString(), base, List.of()));
        ReviewDiff second = reader.read(new ReviewDiffRequest(repo.toString(), base, List.of()));

        assertTrue(first.gitRepository());
        assertEquals(base, first.baseRef());
        assertEquals(2, first.fileCount());
        assertEquals(first.diffHash(), second.diffHash());
        assertTrue(first.content().contains("Task.java"));
        assertTrue(first.content().contains("NewTest.java"));
        Map<String, ReviewDiffFile> files = first.files().stream()
                .collect(Collectors.toMap(ReviewDiffFile::path, Function.identity()));
        assertEquals(1, files.get("Task.java").additions());
        assertEquals(1, files.get("Task.java").deletions());
        assertEquals(1, files.get("NewTest.java").additions());
    }

    @Test
    void 执行器上报文件存在时只审阅本次任务范围() throws Exception {
        Path repo = gitRepository();
        String base = git(repo, "rev-parse", "HEAD").strip();
        Files.writeString(repo.resolve("Task.java"), "class Task { int value = 2; }\n");
        Files.writeString(repo.resolve("Unrelated.java"), "class Unrelated {}\n");

        ReviewDiff diff = reader.read(new ReviewDiffRequest(
                repo.toString(), base, List.of("Task.java")));

        assertEquals(1, diff.fileCount());
        assertEquals("Task.java", diff.files().get(0).path());
        assertFalse(diff.content().contains("Unrelated.java"));
    }

    @Test
    void 二进制变更只保留元信息不展开正文() throws Exception {
        Path repo = gitRepository();
        String base = git(repo, "rev-parse", "HEAD").strip();
        Files.write(repo.resolve("asset.bin"), new byte[]{0, 1, 2, 3});

        ReviewDiff diff = reader.read(new ReviewDiffRequest(repo.toString(), base, List.of()));

        ReviewDiffFile binary = diff.files().stream()
                .filter(file -> file.path().equals("asset.bin"))
                .findFirst()
                .orElseThrow();
        assertTrue(binary.binary());
        assertEquals(0, binary.additions());
        assertFalse(diff.content().contains("\u0000"));
    }

    @Test
    void 非Git目录降级为执行器上报文件清单() throws IOException {
        Files.writeString(temp.resolve("notes.md"), "changed\n");

        ReviewDiff diff = reader.read(new ReviewDiffRequest(
                temp.toString(), null, List.of("notes.md")));

        assertFalse(diff.gitRepository());
        assertEquals(1, diff.fileCount());
        assertEquals("notes.md", diff.files().get(0).path());
        assertTrue(diff.content().contains("bytes"));
    }

    @Test
    void 非Git目录同尺寸内容变化也会改变哈希() throws IOException {
        Path file = temp.resolve("notes.md");
        Files.writeString(file, "aaaa\n");
        ReviewDiff first = reader.read(new ReviewDiffRequest(temp.toString(), null, List.of("notes.md")));
        Files.writeString(file, "bbbb\n");
        ReviewDiff second = reader.read(new ReviewDiffRequest(temp.toString(), null, List.of("notes.md")));

        assertFalse(first.diffHash().equals(second.diffHash()));
    }

    @Test
    void 对话凭据在落审阅正文前脱敏() throws Exception {
        Path repo = gitRepository();
        String base = git(repo, "rev-parse", "HEAD").strip();
        Files.writeString(repo.resolve("config.env"), "api_key=sk-live-secret-value\n");
        git(repo, "add", "config.env");

        ReviewDiff diff = reader.read(new ReviewDiffRequest(repo.toString(), base, List.of()));

        assertFalse(diff.content().contains("sk-live-secret-value"));
        assertTrue(diff.content().contains("api_key=***"));
    }

    @Test
    void 基线提交不存在时拒绝生成不可信快照() throws Exception {
        Path repo = gitRepository();

        assertThrows(DomainException.class,
                () -> reader.read(new ReviewDiffRequest(repo.toString(), "deadbeef", List.of())));
    }

    private Path gitRepository() throws Exception {
        Path repo = Files.createDirectory(temp.resolve("repo"));
        git(repo, "init");
        git(repo, "config", "user.email", "review@test.local");
        git(repo, "config", "user.name", "Review Test");
        Files.writeString(repo.resolve("Task.java"), "class Task { int value = 1; }\n");
        git(repo, "add", "Task.java");
        git(repo, "commit", "-m", "初始提交");
        return repo;
    }

    private static String git(Path directory, String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " 失败：" + output);
        }
        return output;
    }
}
