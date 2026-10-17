package com.pydlove.regent.execution.infrastructure.git;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.port.BranchManager;
import com.pydlove.regent.execution.domain.port.BranchPlan;
import com.pydlove.regent.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Git 分支适配器 —— 「绝不直接改主干」的执行者。
 *
 * <p>任务开跑前先切一条 {@code codex/task-N}，一切改动都落在那里。人在地板上看着的时候，
 * 这条纪律是「AI 会不会直接改坏主干」的唯一答案，所以它宁可失败也不将就：
 * 目录不是 Git 仓库、切分支失败、名下有未完成的 rebase —— 一律拒绝派发，而不是硬着头皮在
 * 当前分支上跑。
 *
 * <p>{@code enabled=false} 时返回<b>当前分支名</b>而不是编一个。这是刻意的：
 * 关掉隔离意味着「就在这条分支上跑」，那就如实记下是哪条分支，任务详情里才不会骗人。
 */
public class GitBranchManager implements BranchManager {

    private static final Logger log = LoggerFactory.getLogger(GitBranchManager.class);
    private static final long TIMEOUT_SECONDS = 60;

    private final boolean enabled;

    public GitBranchManager(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public BranchPlan plan(String projectPath, TaskId taskId) {
        Path workTree = requireWorkTree(projectPath);
        if (!enabled) {
            return new BranchPlan(currentBranch(workTree), false);
        }
        String branch = "codex/task-" + taskId.value();
        return new BranchPlan(branch, !branchExists(workTree, branch));
    }

    @Override
    public String createFor(String projectPath, TaskId taskId) {
        Path workTree = requireWorkTree(projectPath);
        if (!enabled) {
            log.info("分支隔离已关闭，任务 {} 将在当前分支上执行", taskId);
            return currentBranch(workTree);
        }
        String branch = "codex/task-" + taskId.value();
        if (branchExists(workTree, branch)) {
            // 重试、重新派发都会走到这里：分支还在，切回去接着用，而不是把上一轮的改动丢掉重开
            git(workTree, "checkout", branch);
        } else {
            git(workTree, "checkout", "-b", branch);
        }
        log.info("任务 {} 已在分支 {} 上执行", taskId, branch);
        return branch;
    }

    @Override
    public void switchTo(String projectPath, String branchName) {
        if (!enabled) {
            return;
        }
        Path workTree = requireWorkTree(projectPath);
        if (branchName.equals(currentBranch(workTree))) {
            return;
        }
        git(workTree, "checkout", branchName);
    }

    private Path requireWorkTree(String projectPath) {
        Path directory = Path.of(projectPath);
        if (!Files.isDirectory(directory)) {
            throw new DomainException("项目目录不存在：" + projectPath);
        }
        String inside = gitOutput(directory, "rev-parse", "--is-inside-work-tree").strip();
        if (!"true".equals(inside)) {
            throw new DomainException("项目目录不是 Git 仓库，无法建立独立分支：" + projectPath);
        }
        return directory;
    }

    private String currentBranch(Path workTree) {
        return gitOutput(workTree, "rev-parse", "--abbrev-ref", "HEAD").strip();
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

    private String gitOutput(Path workTree, String... args) {
        ProcessResult result = run(workTree, args);
        if (result.exitCode() != 0) {
            throw new DomainException("git " + String.join(" ", args) + " 失败（退出码 " + result.exitCode()
                    + "）：" + firstLine(result.output()));
        }
        return result.output();
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
