package com.pydlove.regent.project.infrastructure.git;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitProjectGitBranchReaderTest {

    private final GitProjectGitBranchReader reader = new GitProjectGitBranchReader();

    @Test
    @DisplayName("非 Git 目录返回空，让详情界面隐藏分支项")
    void returnsEmptyForNonGitDirectory(@TempDir Path directory) {
        assertEquals(Optional.empty(), reader.currentBranch(directory.toString()));
    }

    @Test
    @DisplayName("Git 工作区返回真实当前分支")
    void returnsCurrentGitBranch(@TempDir Path repository) throws Exception {
        git(repository, "init");
        git(repository, "symbolic-ref", "HEAD", "refs/heads/feature/project-detail");

        assertEquals(Optional.of("feature/project-detail"), reader.currentBranch(repository.toString()));
    }

    private static void git(Path repository, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
        assertEquals(0, process.exitValue(), output);
    }
}
