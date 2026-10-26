package com.pydlove.regent.shared.kernel;

import com.pydlove.regent.shared.domain.DomainException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * 任务文档上传策略。
 *
 * <p>只接受明确的白名单扩展名，内容类型由服务端按扩展名决定，不采信浏览器上报值。
 * PDF 与现代 Office/OpenDocument 校验魔数；纯文本拒绝 NUL 字节。HTML/SVG 等可执行内容不接收。
 */
public final class DocumentUploadPolicy {

    public static final long DEFAULT_MAX_BYTES = 25L * 1024 * 1024;

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".doc", "application/msword"),
            Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry(".xls", "application/vnd.ms-excel"),
            Map.entry(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry(".ppt", "application/vnd.ms-powerpoint"),
            Map.entry(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry(".odt", "application/vnd.oasis.opendocument.text"),
            Map.entry(".ods", "application/vnd.oasis.opendocument.spreadsheet"),
            Map.entry(".odp", "application/vnd.oasis.opendocument.presentation"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".md", "text/markdown"),
            Map.entry(".csv", "text/csv"),
            Map.entry(".json", "application/json"),
            Map.entry(".yaml", "application/yaml"),
            Map.entry(".yml", "application/yaml"),
            Map.entry(".xml", "application/xml"),
            Map.entry(".rtf", "application/rtf"));

    private DocumentUploadPolicy() {
    }

    public static ValidatedDocument validate(String filename, long sizeBytes, byte[] content, long maxBytes) {
        if (content == null || content.length == 0) {
            throw new DomainException("文档内容不能为空");
        }
        if (sizeBytes <= 0 || sizeBytes != content.length) {
            throw new DomainException("文档大小与内容不一致");
        }
        if (sizeBytes > maxBytes) {
            throw new DomainException("文档超过大小上限：" + maxBytes + " 字节");
        }
        String displayName = displayName(filename);
        int dot = displayName.lastIndexOf('.');
        if (dot <= 0 || dot == displayName.length() - 1) {
            throw new DomainException("文档缺少可识别的扩展名：" + displayName);
        }
        String extension = displayName.substring(dot).toLowerCase(Locale.ROOT);
        String contentType = CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw new DomainException("不支持的文档类型：" + extension);
        }
        validateMagic(extension, content);
        return new ValidatedDocument(
                displayName, contentType, extension, sizeBytes, sha256(content));
    }

    private static void validateMagic(String extension, byte[] content) {
        if (".pdf".equals(extension) && !startsWith(content, new byte[]{'%', 'P', 'D', 'F', '-'})) {
            throw new DomainException("PDF 文件头不正确");
        }
        if (isZipDocument(extension) && !startsWith(content, new byte[]{'P', 'K', 3, 4})) {
            throw new DomainException("Office/OpenDocument 文件不是有效的 ZIP 容器");
        }
        if (isTextDocument(extension)) {
            for (byte value : content) {
                if (value == 0) {
                    throw new DomainException("文本文档包含二进制内容");
                }
            }
        }
    }

    private static boolean isZipDocument(String extension) {
        return switch (extension) {
            case ".docx", ".xlsx", ".pptx", ".odt", ".ods", ".odp" -> true;
            default -> false;
        };
    }

    private static boolean isTextDocument(String extension) {
        return switch (extension) {
            case ".txt", ".md", ".csv", ".json", ".yaml", ".yml", ".xml", ".rtf" -> true;
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String displayName(String filename) {
        String value = filename == null ? "" : filename.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        value = (slash >= 0 ? value.substring(slash + 1) : value).trim();
        if (value.isEmpty()) {
            throw new DomainException("文档文件名不能为空");
        }
        if (value.length() > 255) {
            throw new DomainException("文档文件名超长（上限 255 字符）");
        }
        return value;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }

    public record ValidatedDocument(
            String filename,
            String contentType,
            String extension,
            long sizeBytes,
            String contentHash) {
    }
}
