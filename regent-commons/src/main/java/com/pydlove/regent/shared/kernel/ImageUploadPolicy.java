package com.pydlove.regent.shared.kernel;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.Locale;
import java.util.Map;

/**
 * 图片上传的统一策略。
 *
 * <p>任务附件与 Bug 附件收的是同一种东西，白名单、大小、魔数和展示名规则必须一致。
 * 把这些放在共享内核，避免两个上下文各维护一份后悄悄漂移。
 */
public final class ImageUploadPolicy {

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/gif", ".gif",
            "image/webp", ".webp");

    private static final int MAX_FILENAME = 200;
    private static final String DEFAULT_FILENAME = "image";

    private ImageUploadPolicy() {
    }

    public static ValidatedImage validate(String filename, String rawContentType,
                                          byte[] rawContent, long maxBytes) {
        String contentType = normalizeContentType(rawContentType);
        String extension = IMAGE_TYPES.get(contentType);
        if (extension == null) {
            throw new DomainException("只收 PNG / JPEG / GIF / WebP 图片，收到的是：" + contentType);
        }
        byte[] content = rawContent == null ? new byte[0] : rawContent;
        if (content.length == 0) {
            throw new DomainException("上传的图片是空文件");
        }
        if (content.length > maxBytes) {
            throw new DomainException("图片太大（" + content.length + " 字节，上限 " + maxBytes
                    + " 字节），请压缩后重试");
        }
        if (!looksLikeImage(contentType, content)) {
            throw new DomainException("文件内容不是有效的 " + contentType + " 图片：" + filename);
        }
        return new ValidatedImage(
                displayName(filename, extension),
                contentType,
                extension,
                content.length,
                content);
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

    /** 通过校验后交给各上下文落库的纯数据。 */
    public record ValidatedImage(
            String filename,
            String contentType,
            String extension,
            long sizeBytes,
            byte[] content) {
    }
}
