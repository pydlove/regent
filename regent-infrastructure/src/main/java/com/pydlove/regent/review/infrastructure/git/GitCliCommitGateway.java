package com.pydlove.regent.review.infrastructure.git;

import com.pydlove.regent.review.domain.port.GitCommitGateway;
import com.pydlove.regent.shared.domain.DomainException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 使用本机 Git CLI 创建受审阅范围约束的提交。
 *
 * <p>提交前要求真实索引为空，随后只暂存快照内文件。这样未审阅改动不会因为用户已有暂存内容
 * 被顺带提交；提交失败时会尽量把本次暂存撤掉，保持工作区原状。
 */
public class GitCliCommitGateway implements GitCommitGateway {

    private static final long TIMEOUT_SECONDS = 60;

    @Override
    public Inspection inspect(String projectDirectory, List<String> reviewedPaths) {
        Path root = requireRoot(projectDirectory);
        ProcessResult inside = run(root, null, "rev-parse", "--is-inside-work-tree");
        if (inside.exitCode() != 0 || !"true".equals(inside.output().strip())) {
            return new Inspection(false, "", "", true, List.of());
        }
        ProcessResult head = run(root, null, "rev-parse", "--verify", "--quiet", "HEAD");
        if (head.exitCode() != 0 || !head.output().strip().matches("[0-9a-f]{40,64}")) {
            return new Inspection(false, "", "", true, List.of());
        }
        ProcessResult branch = run(root, null, "symbolic-ref", "--short", "-q", "HEAD");
        ProcessResult staged = run(root, null, "diff", "--cached", "--name-only", "-z");
        requireSuccess(staged, "读取暂存区");
        List<String> stagedPaths = splitNul(staged.output());
        return new Inspection(
                true,
                branch.exitCode() == 0 ? branch.output().strip() : "",
                head.output().strip(),
                stagedPaths.isEmpty(),
                stagedPaths);
    }

    @Override
    public CommitResult commit(String projectDirectory, List<String> reviewedPaths, String message) {
        Path root = requireRoot(projectDirectory);
        List<String> paths = normalizePaths(reviewedPaths);
        if (paths.isEmpty()) {
            throw new DomainException("没有可提交的审阅文件");
        }
        if (message == null || message.isBlank()) {
            throw new DomainException("Git 提交信息不能为空");
        }

        Inspection before = inspect(projectDirectory, paths);
        if (!before.gitRepository()) {
            throw new DomainException("项目目录不是可提交的 Git 工作区");
        }
        if (before.branchName().isBlank()) {
            throw new DomainException("仓库处于 detached HEAD，不能自动提交");
        }
        if (!before.indexClean()) {
            throw new DomainException("暂存区已有其他改动，请先提交或取消暂存");
        }

        List<String> addArgs = new ArrayList<>(List.of("add", "-A", "--"));
        addArgs.addAll(paths);
        ProcessResult add = run(root, null, addArgs.toArray(String[]::new));
        requireSuccess(add, "暂存审阅文件");

        ProcessResult staged = run(root, null, "diff", "--cached", "--name-only", "-z");
        requireSuccess(staged, "核对暂存文件");
        List<String> stagedPaths = splitNul(staged.output());
        if (stagedPaths.isEmpty()) {
            throw new DomainException("审阅文件当前没有可提交的变更");
        }
        if (!new LinkedHashSet<>(stagedPaths).equals(new LinkedHashSet<>(paths))) {
            clearIndex(root, paths);
            throw new DomainException("暂存范围与审阅文件不一致，已取消本次暂存");
        }

        List<String> commitArgs = new ArrayList<>(List.of("commit", "-F", "-", "--"));
        commitArgs.addAll(paths);
        ProcessResult committed = run(root,
                message.strip().getBytes(StandardCharsets.UTF_8),
                commitArgs.toArray(String[]::new));
        if (committed.exitCode() != 0) {
            clearIndex(root, paths);
            throw new DomainException("Git 提交失败（退出码 " + committed.exitCode() + "）："
                    + firstLine(committed.output()));
        }

        ProcessResult head = run(root, null, "rev-parse", "HEAD");
        requireSuccess(head, "读取提交 SHA");
        ProcessResult branch = run(root, null, "symbolic-ref", "--short", "-q", "HEAD");
        return new CommitResult(
                head.output().strip(),
                branch.exitCode() == 0 ? branch.output().strip() : "");
    }

    @Override
    public boolean matchesCommit(String projectDirectory,
                                 String commitSha,
                                 List<String> reviewedPaths) {
        if (commitSha == null || !commitSha.matches("[0-9a-f]{40,64}")) {
            return false;
        }
        Path root = requireRoot(projectDirectory);
        ProcessResult head = run(root, null, "rev-parse", "--verify", "--quiet", "HEAD");
        if (head.exitCode() != 0 || !commitSha.equals(head.output().strip())) {
            return false;
        }
        List<String> paths = normalizePaths(reviewedPaths);
        if (paths.isEmpty()) {
            return false;
        }
        List<String> statusArgs = new ArrayList<>(List.of("status", "--porcelain", "-z", "--"));
        statusArgs.addAll(paths);
        ProcessResult status = run(root, null, statusArgs.toArray(String[]::new));
        return status.exitCode() == 0 && status.output().isEmpty();
    }

    private static void clearIndex(Path root, List<String> paths) {
        List<String> resetArgs = new ArrayList<>(List.of("reset", "-q", "--"));
        resetArgs.addAll(paths);
        run(root, null, resetArgs.toArray(String[]::new));
    }

    private static Path requireRoot(String projectDirectory) {
        if (projectDirectory == null || projectDirectory.isBlank()) {
            throw new DomainException("项目目录不能为空");
        }
        Path root = Path.of(projectDirectory).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new DomainException("项目目录不存在：" + root);
        }
        return root;
    }

    private static List<String> normalizePaths(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String path = value.replace('\\', '/').strip();
            if (path.startsWith("/") || path.contains("../") || path.equals("..")) {
                throw new DomainException("提交文件必须位于项目内：" + value);
            }
            normalized.add(path);
        }
        return List.copyOf(normalized);
    }

    private static List<String> splitNul(String output) {
        List<String> values = new ArrayList<>();
        for (String value : output.split("\u0000")) {
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private static void requireSuccess(ProcessResult result, String action) {
        if (result.exitCode() != 0) {
            throw new DomainException(action + "失败（退出码 " + result.exitCode() + "）："
                    + firstLine(result.output()));
        }
    }

    private static String firstLine(String value) {
        String normalized = value == null ? "" : value.strip();
        int newline = normalized.indexOf('\n');
        return newline < 0 ? normalized : normalized.substring(0, newline).strip();
    }

    private static ProcessResult run(Path directory, byte[] stdin, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            if (stdin != null && stdin.length > 0) {
                try (var output = process.getOutputStream()) {
                    output.write(stdin);
                }
            } else {
                process.getOutputStream().close();
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DomainException("Git 命令超时：" + String.join(" ", args));
            }
            return new ProcessResult(process.exitValue(), output);
        } catch (IOException exception) {
            throw new DomainException("Git 命令无法执行：" + String.join(" ", args), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException("Git 命令被中断：" + String.join(" ", args), exception);
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
