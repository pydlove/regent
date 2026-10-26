package com.pydlove.regent.project.infrastructure.git;

import com.pydlove.regent.project.domain.port.ProjectVersionReader;
import com.pydlove.regent.shared.domain.DomainException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** 通过 Git CLI 读取当前提交上的版本 Tag。 */
public class GitProjectVersionReader implements ProjectVersionReader {

    private static final long TIMEOUT_SECONDS = 5;

    @Override
    public Optional<String> currentTag(String projectPath) {
        ProcessResult head = run(projectPath, "rev-parse", "--verify", "--quiet", "HEAD");
        if (head.exitCode() != 0) {
            return Optional.empty();
        }
        ProcessResult tags = run(projectPath, "tag", "--points-at", "HEAD", "--sort=-version:refname");
        if (tags.exitCode() != 0) {
            return Optional.empty();
        }
        return Arrays.stream(tags.output().split("\\R"))
                .map(String::strip)
                .filter(tag -> tag.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}"))
                .findFirst();
    }

    private ProcessResult run(String projectPath, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", projectPath));
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

    private record ProcessResult(int exitCode, String output) {
    }
}
