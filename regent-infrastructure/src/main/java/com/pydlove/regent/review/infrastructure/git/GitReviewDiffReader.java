package com.pydlove.regent.review.infrastructure.git;

import com.pydlove.regent.review.domain.model.ReviewDiff;
import com.pydlove.regent.review.domain.model.ReviewDiffFile;
import com.pydlove.regent.review.domain.model.ReviewDiffRequest;
import com.pydlove.regent.review.domain.port.ReviewDiffReader;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.shared.domain.DomainException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * 用只读 Git 命令读取任务变更事实。
 *
 * <p>已跟踪文件使用 base commit 到工作区的 diff，未跟踪文件补成 new-file hunk；二进制文件只保留
 * 路径、大小与“二进制”标记。完整 diff 在截断前计算哈希，因此正文展示截断不会削弱失效判断。
 */
public class GitReviewDiffReader implements ReviewDiffReader {

    private static final long TIMEOUT_SECONDS = 30;
    private static final int BINARY_PROBE_BYTES = 8 * 1024;
    private static final long MAX_UNTRACKED_TEXT_BYTES = 1024 * 1024;

    @Override
    public ReviewDiff read(ReviewDiffRequest request) {
        Path root = Path.of(request.projectDirectory()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new DomainException("审阅项目目录不存在：" + root);
        }
        Optional<GitRefs> refs = gitRefs(root);
        if (refs.isEmpty()) {
            return fileListFallback(root, request.reportedChangedFiles());
        }

        GitRefs git = refs.get();
        String base = normalizeBase(root, request.baseRef(), git.head());
        StringBuilder full = new StringBuilder();
        Map<String, ReviewDiffFile> files = new TreeMap<>();
        List<String> scopedPaths = request.reportedChangedFiles().stream()
                .map(GitReviewDiffReader::normalizeRelative)
                .distinct()
                .toList();

        List<String> trackedArgs = new ArrayList<>(List.of(
                "diff", "--no-ext-diff", "--no-renames", "--no-color", base, "--"));
        trackedArgs.addAll(scopedPaths);
        ProcessResult tracked = run(root, trackedArgs.toArray(String[]::new));
        requireSuccess(tracked, "读取 Git diff");
        full.append(tracked.output());

        List<String> numstatArgs = new ArrayList<>(List.of(
                "diff", "--numstat", "--no-renames", base, "--"));
        numstatArgs.addAll(scopedPaths);
        ProcessResult numstat = run(root, numstatArgs.toArray(String[]::new));
        requireSuccess(numstat, "读取 Git diff 统计");
        parseNumstat(numstat.output(), files);

        ProcessResult untrackedResult = run(root, "ls-files", "--others", "--exclude-standard", "-z");
        requireSuccess(untrackedResult, "读取未跟踪文件");
        List<String> untracked = splitNul(untrackedResult.output()).stream()
                .filter(path -> scopedPaths.isEmpty() || scopedPaths.contains(path))
                .toList();
        for (String relative : untracked) {
            appendUntracked(root, relative, full, files);
        }

        String content = full.toString();
        String hash = hash(git.head(), base, files, content);
        String safeContent = redact(content);
        return new ReviewDiff(base, git.head(), true, hash,
                List.copyOf(files.values()), additions(files), deletions(files),
                truncate(safeContent), safeContent.length() > ReviewSnapshot.MAX_DIFF_CONTENT_LENGTH);
    }

    private ReviewDiff fileListFallback(Path root, List<String> reportedFiles) {
        StringBuilder content = new StringBuilder();
        Map<String, ReviewDiffFile> files = new TreeMap<>();
        for (String relative : reportedFiles) {
            String normalized = normalizeRelative(relative);
            Path file = root.resolve(normalized).normalize();
            if (!file.startsWith(root)) {
                throw new DomainException("变更文件越出项目目录：" + relative);
            }
            boolean exists = Files.isRegularFile(file);
            boolean binary = exists && isBinary(file);
            boolean generated = isGenerated(normalized);
            long size = 0;
            String digest = null;
            try {
                size = exists ? Files.size(file) : 0;
                digest = exists ? sha256(file) : null;
            } catch (IOException e) {
                throw new DomainException("读取变更文件大小失败：" + normalized, e);
            }
            files.put(normalized, new ReviewDiffFile(normalized, 0, 0, binary, generated));
            content.append(exists ? "M " : "? ").append(normalized)
                    .append(" (").append(size).append(" bytes")
                    .append(digest == null ? "" : ", sha256=" + digest)
                    .append(binary ? ", binary" : "").append(")\n");
        }
        String text = content.toString();
        String hash = hash(null, null, files, text);
        return new ReviewDiff(null, null, false, hash, List.copyOf(files.values()),
                0, 0, truncate(text), text.length() > ReviewSnapshot.MAX_DIFF_CONTENT_LENGTH);
    }

    private Optional<GitRefs> gitRefs(Path root) {
        ProcessResult inside = run(root, "rev-parse", "--is-inside-work-tree");
        if (inside.exitCode() != 0 || !"true".equals(inside.output().strip())) {
            return Optional.empty();
        }
        ProcessResult head = run(root, "rev-parse", "--verify", "--quiet", "HEAD");
        if (head.exitCode() != 0) {
            return Optional.empty();
        }
        String value = head.output().strip();
        return value.matches("[0-9a-f]{40,64}") ? Optional.of(new GitRefs(value)) : Optional.empty();
    }

    private String normalizeBase(Path root, String requestedBase, String head) {
        if (requestedBase == null || requestedBase.isBlank()) {
            return head;
        }
        ProcessResult result = run(root, "rev-parse", "--verify", "--quiet",
                requestedBase.strip() + "^{commit}");
        if (result.exitCode() != 0) {
            throw new DomainException("审阅基线提交不存在：" + requestedBase);
        }
        String base = result.output().strip();
        if (!base.matches("[0-9a-f]{40,64}")) {
            throw new DomainException("审阅基线提交格式不合法：" + requestedBase);
        }
        return base;
    }

    private void parseNumstat(String output, Map<String, ReviewDiffFile> files) {
        for (String line : output.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\\t", 3);
            if (fields.length < 3) {
                continue;
            }
            String path = normalizeRelative(fields[2]);
            boolean binary = "-".equals(fields[0]) || "-".equals(fields[1]);
            int additions = binary ? 0 : parseCount(fields[0]);
            int deletions = binary ? 0 : parseCount(fields[1]);
            files.put(path, new ReviewDiffFile(path, additions, deletions, binary, isGenerated(path)));
        }
    }

    private void appendUntracked(Path root,
                                 String relative,
                                 StringBuilder full,
                                 Map<String, ReviewDiffFile> files) {
        String path = normalizeRelative(relative);
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return;
        }
        boolean binary = isBinary(file);
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new DomainException("读取未跟踪文件大小失败：" + path, e);
        }
        if (binary || size > MAX_UNTRACKED_TEXT_BYTES) {
            files.put(path, new ReviewDiffFile(path, 0, 0, true, isGenerated(path)));
            full.append("diff --git a/").append(path).append(" b/").append(path).append('\n')
                    .append("new file mode 100644\n")
                    .append("Binary files /dev/null and b/").append(path)
                    .append(" differ (").append(size).append(" bytes)\n");
            return;
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DomainException("读取未跟踪文本文件失败：" + path, e);
        }
        List<String> lines = text.lines().toList();
        files.put(path, new ReviewDiffFile(
                path, lines.size(), 0, false, isGenerated(path)));
        full.append("diff --git a/").append(path).append(" b/").append(path).append('\n')
                .append("new file mode 100644\n")
                .append("--- /dev/null\n")
                .append("+++ b/").append(path).append('\n')
                .append("@@ -0,0 +1,").append(lines.size()).append(" @@\n");
        for (String line : lines) {
            full.append('+').append(line).append('\n');
        }
    }

    private static String hash(String head,
                               String base,
                               Map<String, ReviewDiffFile> files,
                               String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(("base=" + nullToEmpty(base) + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(("head=" + nullToEmpty(head) + "\n").getBytes(StandardCharsets.UTF_8));
            for (ReviewDiffFile file : files.values()) {
                digest.update((file.path() + "\t" + file.additions() + "\t" + file.deletions()
                        + "\t" + file.binary() + "\t" + file.generated() + "\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            digest.update(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new DomainException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private static boolean isBinary(Path file) {
        try (var input = Files.newInputStream(file)) {
            byte[] probe = input.readNBytes(BINARY_PROBE_BYTES);
            for (byte value : probe) {
                if (value == 0) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new DomainException("读取文件二进制特征失败：" + file, e);
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private static boolean isGenerated(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("/generated/")
                || lower.startsWith("generated/")
                || lower.contains("/dist/")
                || lower.startsWith("dist/")
                || lower.contains("/build/")
                || lower.startsWith("build/")
                || lower.contains("/target/")
                || lower.startsWith("target/")
                || lower.contains("/node_modules/")
                || lower.endsWith(".min.js")
                || lower.endsWith(".min.css")
                || lower.endsWith("package-lock.json")
                || lower.endsWith("pnpm-lock.yaml")
                || lower.endsWith("yarn.lock");
    }

    private static int parseCount(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int additions(Map<String, ReviewDiffFile> files) {
        return files.values().stream().mapToInt(ReviewDiffFile::additions).sum();
    }

    private static int deletions(Map<String, ReviewDiffFile> files) {
        return files.values().stream().mapToInt(ReviewDiffFile::deletions).sum();
    }

    private static String truncate(String content) {
        return content.length() <= ReviewSnapshot.MAX_DIFF_CONTENT_LENGTH
                ? content
                : content.substring(0, ReviewSnapshot.MAX_DIFF_CONTENT_LENGTH) + "\n…（diff 已截断）";
    }

    private static String redact(String content) {
        return content
                .replaceAll("(?is)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----",
                        "[REDACTED PRIVATE KEY]")
                .replaceAll("(?i)(password|passwd|secret|token|api[_-]?key|private[_-]?key)"
                        + "(\\s*[:=]\\s*)([^\\s\"']+)", "$1$2***")
                .replaceAll("\\bsk-[A-Za-z0-9_-]{8,}\\b", "sk-***");
    }

    private static String normalizeRelative(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("变更文件路径不能为空");
        }
        String path = raw.replace('\\', '/').strip();
        if (path.startsWith("/") || path.contains("../")) {
            throw new DomainException("变更文件路径必须位于项目内：" + raw);
        }
        return path;
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

    private static ProcessResult run(Path directory, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new DomainException("Git 命令超时：" + String.join(" ", args));
            }
            return new ProcessResult(process.exitValue(), output);
        } catch (IOException e) {
            throw new DomainException("Git 命令无法执行：" + String.join(" ", args), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException("Git 命令被中断：" + String.join(" ", args), e);
        }
    }

    private static String firstLine(String text) {
        String normalized = text == null ? "" : text.strip();
        int newline = normalized.indexOf('\n');
        return newline < 0 ? normalized : normalized.substring(0, newline);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record GitRefs(String head) {
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
