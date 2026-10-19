package com.pydlove.regent.execution.infrastructure.git;

import com.pydlove.regent.execution.domain.model.TaskId;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证项目目录没有 Git、Git 为空或只是多仓库父目录时，分支能力安静降级。 */
class GitBranchManagerTest {

    private final GitBranchManager manager = new GitBranchManager(true);

    @BeforeEach
    void 确认本机有Git() {
        Assumptions.assumeTrue(command(null, "git", "--version").exitCode() == 0, "本机没有 Git，跳过");
    }

    @Test
    @DisplayName("普通目录没有分支，切换也不会失败")
    void 非Git目录返回空并跳过切换(@TempDir Path directory) {
        assertEquals(Optional.empty(), manager.currentBranch(directory.toString()));
        assertEquals(Optional.empty(),
                manager.createRequestedFeature(directory.toString(), TaskId.of(7L), "feature/ai"));

        manager.switchTo(directory.toString(), "feature/not-exists");
    }

    @Test
    @DisplayName("空 Git 仓库没有 HEAD，不编造分支名")
    void 空仓库返回空(@TempDir Path repository) throws Exception {
        git(repository, "init");

        assertEquals(Optional.empty(), manager.currentBranch(repository.toString()));
        assertEquals(Optional.empty(),
                manager.createRequestedFeature(repository.toString(), TaskId.of(7L), "feature/ai"));
    }

    @Test
    @DisplayName("游离 HEAD 没有分支名，但仍可在需要时创建功能分支")
    void 游离HEAD返回空(@TempDir Path repository) throws Exception {
        initRepository(repository);
        git(repository, "checkout", "--detach", "HEAD");

        assertEquals(Optional.empty(), manager.currentBranch(repository.toString()));
        assertEquals(Optional.of("feature/ai"),
                manager.createRequestedFeature(repository.toString(), TaskId.of(7L), "feature/ai"));
    }

    @Test
    @DisplayName("多个 Git 仓库的父目录本身没有分支")
    void 多仓库父目录返回空(@TempDir Path parent) throws Exception {
        Path repository = Files.createDirectory(parent.resolve("service-a"));
        initRepository(repository);

        assertEquals(Optional.empty(), manager.currentBranch(parent.toString()));
        assertEquals(Optional.empty(),
                manager.createRequestedFeature(parent.toString(), TaskId.of(7L), "feature/ai"));
    }

    @Test
    @DisplayName("有效 Git 仓库能读取、创建和切回分支")
    void 有效仓库正常使用分支(@TempDir Path repository) throws Exception {
        initRepository(repository);
        git(repository, "checkout", "-b", "feature/base");

        assertEquals(Optional.of("feature/base"), manager.currentBranch(repository.toString()));

        Optional<String> created = manager.createRequestedFeature(
                repository.toString(), TaskId.of(7L), "feature/ai");

        assertEquals(Optional.of("feature/ai"), created);
        assertEquals(Optional.of("feature/ai"), manager.currentBranch(repository.toString()));

        manager.switchTo(repository.toString(), "feature/base");
        assertEquals(Optional.of("feature/base"), manager.currentBranch(repository.toString()));
    }

    private static void initRepository(Path repository) throws Exception {
        git(repository, "init");
        git(repository, "-c", "user.name=Regent Test", "-c", "user.email=regent@example.test",
                "commit", "--allow-empty", "-m", "initial");
    }

    private static CommandResult git(Path repository, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", repository.toString()));
        command.addAll(List.of(args));
        CommandResult result = command(null, command.toArray(String[]::new));
        assertEquals(0, result.exitCode(), result.output());
        return result;
    }

    private static CommandResult command(Path directory, String... command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (directory != null) {
            builder.directory(directory.toFile());
        }
        builder.redirectErrorStream(true);
        clearInheritedGitConfig(builder);
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

    private record CommandResult(int exitCode, String output) {
    }
}
