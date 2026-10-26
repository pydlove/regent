package com.pydlove.regent.execution.infrastructure.git;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.port.BranchManager;
import com.pydlove.regent.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Git 分支适配器。
 *
 * <p>项目根目录可以不是 Git 仓库，也可以是多个 Git 仓库的父目录。只有根目录本身是有效
 * Git 工作区且 {@code HEAD} 能解析出提交时才记录/切换分支；其他情况返回空并让任务在目录
 * 本身执行，不能因为「没有分支」拒绝派发。
 *
 * <p>{@code enabled=false} 时仍读取并记录当前分支，但不创建或切换分支。
 */
public class GitBranchManager implements BranchManager {

    private static final Logger log = LoggerFactory.getLogger(GitBranchManager.class);
    private static final long TIMEOUT_SECONDS = 60;

    private final boolean enabled;

    public GitBranchManager(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public Optional<String> currentBranch(String projectPath) {
        return repository(projectPath).flatMap(this::currentBranch);
    }

    @Override
    public Optional<String> currentCommit(String projectPath) {
        return repository(projectPath).flatMap(this::currentCommit);
    }

    @Override
    public Optional<String> createRequestedFeature(String projectPath, TaskId taskId, String requestedName) {
        Optional<Path> repository = repository(projectPath);
        if (repository.isEmpty()) {
            log.info("项目目录 {} 不是可用的 Git 仓库，忽略任务 {} 的分支请求", projectPath, taskId);
            return Optional.empty();
        }
        Path workTree = repository.get();
        if (!enabled) {
            return currentBranch(workTree);
        }
        String name = requestedName == null || requestedName.isBlank()
                ? "feature/task-" + taskId.value()
                : requestedName.trim();
        if (!name.startsWith("feature/")) {
            name = "feature/" + name;
        }
        if (!name.matches("[A-Za-z0-9._/-]+") || name.contains("..")
                || name.startsWith("/") || name.endsWith("/")) {
            throw new DomainException("AI 建议的分支名不合法：" + requestedName);
        }
        if (branchExists(workTree, name)) {
            git(workTree, "checkout", name);
        } else {
            git(workTree, "checkout", "-b", name);
        }
        return Optional.of(name);
    }

    @Override
    public void switchTo(String projectPath, String branchName) {
        if (!enabled || branchName == null || branchName.isBlank()) {
            return;
        }
        Optional<Path> repository = repository(projectPath);
        if (repository.isEmpty()) {
            log.info("项目目录 {} 没有可用的 Git 分支，跳过切换 {}", projectPath, branchName);
            return;
        }
        Path workTree = repository.get();
        if (branchName.equals(currentBranch(workTree).orElse(null))) {
            return;
        }
        if (!branchExists(workTree, branchName)) {
            log.warn("任务记录的分支 {} 已不存在，继续在项目目录当前状态执行", branchName);
            return;
        }
        git(workTree, "checkout", branchName);
    }

    private Optional<Path> repository(String projectPath) {
        Path directory = Path.of(projectPath);
        if (!Files.isDirectory(directory)) {
            throw new DomainException("项目目录不存在：" + projectPath);
        }
        ProcessResult inside = run(directory, "rev-parse", "--is-inside-work-tree");
        if (inside.exitCode() != 0 || !"true".equals(inside.output().strip())) {
            return Optional.empty();
        }
        ProcessResult head = run(directory, "rev-parse", "--verify", "--quiet", "HEAD");
        return head.exitCode() == 0 ? Optional.of(directory) : Optional.empty();
    }

    private Optional<String> currentBranch(Path workTree) {
        ProcessResult result = run(workTree, "symbolic-ref", "--short", "-q", "HEAD");
        if (result.exitCode() != 0) {
            return Optional.empty();
        }
        String branch = result.output().strip();
        return branch.isEmpty() ? Optional.empty() : Optional.of(branch);
    }

    private Optional<String> currentCommit(Path workTree) {
        ProcessResult result = run(workTree, "rev-parse", "--verify", "HEAD");
        if (result.exitCode() != 0) {
            return Optional.empty();
        }
        String commit = result.output().strip();
        return commit.matches("[0-9a-f]{40,64}") ? Optional.of(commit) : Optional.empty();
    }

    private boolean branchExists(Path workTree, String branch) {
        ProcessResult result = run(workTree, "rev-parse", "--verify", "--quiet", "refs/heads/" + branch);
        return result.exitCode() == 0;
    }

    private void git(Path workTree, String... args) {
        ProcessResult result = run(workTree, args);
        if (result.exitCode() != 0) {
            throw new DomainException("git " + String.join(" ", args) + " 失败（退出码 " + result.exitCode()
                    + "）：" + firstLine(result.output()));
        }
    }

    private ProcessResult run(Path workTree, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", workTree.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DomainException("git 命令超时：" + String.join(" ", args));
            }
            return new ProcessResult(process.exitValue(), output);
        } catch (IOException e) {
            throw new DomainException("git 命令无法执行：" + String.join(" ", args) + "，" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException("git 命令被中断：" + String.join(" ", args), e);
        }
    }

    private static String firstLine(String text) {
        String trimmed = text == null ? "" : text.strip();
        int end = trimmed.indexOf('\n');
        return end < 0 ? trimmed : trimmed.substring(0, end);
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
