package com.pydlove.regent.deployment.infrastructure.log;

import com.pydlove.regent.deployment.domain.model.DeploymentRunId;
import com.pydlove.regent.deployment.domain.port.DeploymentLogStore;
import com.pydlove.regent.deployment.domain.port.DeploymentLogWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 部署日志落盘实现。
 *
 * <p>日志写到 {@code regent.home/deployments/runs/<runId>.log}。执行中使用追加写并立即 flush，
 * 因此界面可以在进程结束前读到已经产生的输出；读取失败或文件不存在时返回空串，不用一条
 * IO 异常把整次部署记录顶掉。
 */
public class FileDeploymentLogStore implements DeploymentLogStore {

    private final Path root;
    private final int maxChars;

    public FileDeploymentLogStore(Path root, int maxChars) {
        this.root = root.toAbsolutePath().normalize();
        this.maxChars = Math.max(1_024, maxChars);
    }

    @Override
    public DeploymentLogWriter open(DeploymentRunId runId) {
        try {
            Files.createDirectories(root);
            return new Writer(path(runId), maxChars);
        } catch (IOException e) {
            throw new UncheckedIOException("无法创建部署日志目录：" + root, e);
        }
    }

    @Override
    public String read(DeploymentRunId runId) {
        Path path = path(runId);
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() <= maxChars) {
                return content;
            }
            return content.substring(0, maxChars) + "\n…（日志已截断）";
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public void delete(DeploymentRunId runId) {
        try {
            Files.deleteIfExists(path(runId));
        } catch (IOException ignored) {
            // 删除日志失败不影响部署配置的删除；运行时目录会由运维统一清理
        }
    }

    private Path path(DeploymentRunId runId) {
        return root.resolve(runId.value() + ".log");
    }

    private static final class Writer implements DeploymentLogWriter {

        private final Path path;
        private final int maxChars;
        private final BufferedWriter writer;
        private int written;
        private boolean truncated;
        private boolean closed;

        private Writer(Path path, int maxChars) throws IOException {
            this.path = path;
            this.maxChars = maxChars;
            this.writer = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        }

        @Override
        public synchronized void append(String text) {
            if (closed || text == null || text.isEmpty()) {
                return;
            }
            try {
                int remaining = maxChars - written;
                if (remaining <= 0) {
                    writeTruncatedOnce();
                    return;
                }
                String value = text.length() <= remaining ? text : text.substring(0, remaining);
                writer.write(value);
                writer.flush();
                written += value.length();
                if (value.length() < text.length()) {
                    writeTruncatedOnce();
                }
            } catch (IOException e) {
                throw new UncheckedIOException("写入部署日志失败：" + path, e);
            }
        }

        private void writeTruncatedOnce() throws IOException {
            if (!truncated) {
                writer.write("\n…（日志已截断）\n");
                writer.flush();
                truncated = true;
            }
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                writer.close();
            } catch (IOException ignored) {
                // 已经产生的日志仍然可读；关闭失败不改变部署结论
            }
        }
    }
}
