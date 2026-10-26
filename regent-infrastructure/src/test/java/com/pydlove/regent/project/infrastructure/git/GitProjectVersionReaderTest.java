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

class GitProjectVersionReaderTest {

    private final GitProjectVersionReader reader = new GitProjectVersionReader();

    @Test
    @DisplayName("当前提交没有 Tag 时返回空")
    void returnsEmptyWithoutTag(@TempDir Path repository) throws Exception {
        git(repository, "init");
        git(repository, "-c", "user.name=Regent Test", "-c", "user.email=regent@example.test",
                "commit", "--allow-empty", "-m", "initial");

        assertEquals(Optional.empty(), reader.currentTag(repository.toString()));
    }

    @Test
    @DisplayName("返回当前提交上的版本 Tag")
    void returnsCurrentCommitTag(@TempDir Path repository) throws Exception {
        git(repository, "init");
        git(repository, "-c", "user.name=Regent Test", "-c", "user.email=regent@example.test",
                "commit", "--allow-empty", "-m", "initial");
        git(repository, "tag", "v1.2.3");

        assertEquals(Optional.of("v1.2.3"), reader.currentTag(repository.toString()));
    }

    private static void git(Path repository, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        clearInheritedGitConfig(builder);
        Process process = builder.start();
        String output;
        try (var input = process.getInputStream()) {
            output = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
        assertEquals(0, process.exitValue(), output);
    }

    private static void clearInheritedGitConfig(ProcessBuilder builder) {
        var environment = builder.environment();
        environment.remove("GIT_CONFIG_COUNT");
        environment.remove("GIT_CONFIG_PARAMETERS");
        environment.keySet().stream()
                .filter(name -> name.startsWith("GIT_CONFIG_KEY_")
                        || name.startsWith("GIT_CONFIG_VALUE_"))
                .toList()
                .forEach(environment::remove);
    }
}
