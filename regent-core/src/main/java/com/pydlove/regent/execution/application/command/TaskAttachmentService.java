package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.AttachmentId;
import com.pydlove.regent.execution.domain.model.TaskAttachment;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.port.AttachmentStorage;
import com.pydlove.regent.execution.domain.repository.TaskAttachmentRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 任务附件用例：上传、列出、读回内容。
 *
 * <p>校验分三层，各守各的：
 * <ol>
 *   <li>这里守「哪些格式收、多大算超限、内容是不是真的图片」—— 它们是策略，会随需求变；</li>
 *   <li>{@code TaskAttachment} 守「这是一张图片」这类结构性不变量；</li>
 *   <li>数据库守外键（附件必须挂在真实任务上）。</li>
 * </ol>
 *
 * <p>为什么不收其它文件类型：这个入口是给「有些任务光靠文字说不清」用的（截图、设计稿），
 * 收进来就要考虑下载、预览、隔离，成本与收益不成比例。SVG 也在拒收之列 ——
 * 它是能带脚本的 XML，服务端原样回吐就是一个 XSS 入口。
 */
public class TaskAttachmentService {

    /** 类型 → 落盘扩展名。白名单，不在表里的一律拒收。 */
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp");

    private static final int MAX_FILENAME = 200;
    private static final String DEFAULT_FILENAME = "image";

    private final TaskRepository taskRepository;
    private final TaskAttachmentRepository attachmentRepository;
    private final AttachmentStorage storage;
    private final long maxBytes;

    public TaskAttachmentService(TaskRepository taskRepository,
                                 TaskAttachmentRepository attachmentRepository,
                                 AttachmentStorage storage,
                                 long maxBytes) {
        this.taskRepository = taskRepository;
        this.attachmentRepository = attachmentRepository;
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    public TaskAttachment attach(TaskId taskId, UploadedAttachment file) {
        requireTask(taskId);
        String contentType = normalizeContentType(file.contentType());
        String extension = IMAGE_TYPES.get(contentType);
        if (extension == null) {
            throw new DomainException("只收 PNG / JPEG / GIF / WebP 图片，收到的是：" + contentType);
        }
        byte[] content = file.content() == null ? new byte[0] : file.content();
        if (content.length == 0) {
            throw new DomainException("上传的图片是空文件");
        }
        if (content.length > maxBytes) {
            throw new DomainException("图片太大（" + content.length + " 字节，上限 " + maxBytes + " 字节），请压缩后重试");
        }
        if (!looksLikeImage(contentType, content)) {
            // 类型标签说是一张图、字节却不是：多半是改扩展名上传的，放进来模型只会看到一团坏数据
            throw new DomainException("文件内容不是有效的 " + contentType + " 图片：" + file.filename());
        }

        String storagePath = storage.store(taskId, extension, content);
        return attachmentRepository.save(TaskAttachment.of(
                taskId, displayName(file.filename(), extension), contentType, content.length, storagePath));
    }

    public List<TaskAttachment> list(TaskId taskId) {
        requireTask(taskId);
        return attachmentRepository.findByTask(taskId);
    }

    public TaskAttachment load(AttachmentId id) {
        return attachmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("附件不存在：" + id));
    }

    /** 读回图片本体，供看图接口用。 */
    public byte[] content(TaskAttachment attachment) {
        return storage.read(attachment.storagePath());
    }

    private void requireTask(TaskId taskId) {
        taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + taskId));
    }

    /** 只取类型本身，丢掉 {@code ; charset=binary} 这类参数，并统一小写。 */
    private static String normalizeContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("缺少图片类型");
        }
        int semicolon = raw.indexOf(';');
        String bare = (semicolon < 0 ? raw : raw.substring(0, semicolon)).trim().toLowerCase(Locale.ROOT);
        return bare;
    }

    /**
     * 展示名。只取路径最后一段、去掉控制字符，超长就截断 ——
     * 这个名字会进数据库与界面，但绝不进磁盘路径（见 AttachmentStorage）。
     */
    private static String displayName(String raw, String extension) {
        String name = raw == null ? "" : raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isBlank()) {
            return DEFAULT_FILENAME + extension;
        }
        return name.length() <= MAX_FILENAME ? name : name.substring(0, MAX_FILENAME);
    }

    /**
     * 魔数校验。浏览器给的类型标签来自扩展名，改个后缀就能骗过去；
     * 真正的判据是开头几个字节。四种格式的签名都是固定值，写死在这里足够。
     */
    private static boolean looksLikeImage(String contentType, byte[] content) {
        return switch (contentType) {
            case "image/png" -> startsWith(content, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/jpeg" -> startsWith(content, 0xFF, 0xD8, 0xFF);
            case "image/gif" -> startsWith(content, 'G', 'I', 'F', '8');
            case "image/webp" -> startsWith(content, 'R', 'I', 'F', 'F')
                    && content.length > 11
                    && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != (signature[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}
