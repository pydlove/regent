package com.pydlove.regent.execution.infrastructure.attachment;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.port.AttachmentStorage;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 本机目录存储。每个任务一个子目录，文件名是 UUID + 扩展名。
 *
 * <p>为什么磁盘上不留原文件名：它可能带中文，而这个路径最终要作为
 * {@code codex exec -i <path>} 的 argv 交给子进程；JVM 按平台字符集编码 argv，
 * 中文在那个环节会被打成问号，模型就找不到文件（详见 CodexCliAdapter 的类注释）。
 * 原文件名留在数据库里只做展示，展示不需要它是合法路径。
 */
public class LocalAttachmentStorage implements AttachmentStorage {

    private final Path root;

    public LocalAttachmentStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String store(TaskId taskId, String extension, byte[] content) {
        Path directory = root.resolve("task-" + taskId.value());
        Path target = directory.resolve(UUID.randomUUID() + extension);
        try {
            Files.createDirectories(directory);
            Files.write(target, content);
        } catch (IOException e) {
            throw new IllegalStateException("附件落盘失败：" + target, e);
        }
        return target.toString();
    }

    @Override
    public byte[] read(String storagePath) {
        Path path = Path.of(storagePath).toAbsolutePath().normalize();
        // 路径来自我们自己的库，但「按库里的一行字符串去读任意文件」这件事本身太危险，
        // 一行校验换掉整类越权读取，很划算
        if (!path.startsWith(root)) {
            throw new DomainException("附件路径不在存储目录内，拒绝读取：" + storagePath);
        }
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            throw new EntityNotFoundException("附件文件不在了：" + storagePath);
        } catch (IOException e) {
            throw new IllegalStateException("附件读取失败：" + storagePath, e);
        }
    }
}
