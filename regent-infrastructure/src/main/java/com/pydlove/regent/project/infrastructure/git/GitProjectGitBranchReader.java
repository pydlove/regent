package com.pydlove.regent.project.infrastructure.git;

import com.pydlove.regent.project.domain.port.ProjectGitBranchReader;
import com.pydlove.regent.shared.domain.DomainException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 通过 Git CLI 读取项目工作区当前分支。
 *
 * <p>先确认 {@code HEAD} 能解析出提交，再读取分支名。非 Git 目录、多仓库父目录、空仓库
 * 和游离 HEAD 都按“没有分支”处理。
 */
public class GitProjectGitBranchReader implements ProjectGitBranchReader {

    private static final long TIMEOUT_SECONDS = 5;

    @Override
    public Optional<String> currentBranch(String projectPath) {
        ProcessResult head = run(projectPath, "rev-parse", "--verify", "--quiet", "HEAD");
        if (head.exitCode() != 0) {
            return Optional.empty();
        }
        ProcessResult result = run(projectPath, "symbolic-ref", "--short", "-q", "HEAD");
        if (result.exitCode() != 0) {
            return Optional.empty();
        }
        String branchName = result.output().strip();
        return branchName.isEmpty() ? Optional.empty() : Optional.of(branchName);
    }

    private ProcessResult run(String projectPath, String... args) {
        List<String> command = new java.util.ArrayList<>(List.of("git", "-C", projectPath));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DomainException("git 命令超时：" + String.join(" ", args));
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new ProcessResult(process.exitValue(), output);
        } catch (IOException e) {
            throw new DomainException("git 命令无法执行：" + String.join(" ", args) + "，" + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException("git 命令被中断：" + String.join(" ", args), e);
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
