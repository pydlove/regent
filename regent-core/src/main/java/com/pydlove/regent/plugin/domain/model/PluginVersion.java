package com.pydlove.regent.plugin.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 不可变的插件内容版本。
 *
 * <p>任务绑定版本号而不是只绑定插件 ID，插件后续升级不会悄悄改变已绑定任务的输入。
 */
public record PluginVersion(
        int revision,
        String filename,
        String content,
        String contentHash,
        Instant createdAt) {

    public static final int MAX_CONTENT_LENGTH = 256 * 1024;
    private static final int MAX_FILENAME_LENGTH = 255;

    public PluginVersion {
        if (revision <= 0) {
            throw new DomainException("插件版本必须为正数：" + revision);
        }
        filename = requireFilename(filename);
        content = requireContent(content);
        contentHash = requireText(contentHash, "内容摘要");
        if (!contentHash.matches("[0-9a-f]{64}")) {
            throw new DomainException("插件内容摘要必须是 64 位小写十六进制");
        }
        createdAt = Objects.requireNonNull(createdAt, "版本创建时间不能为空")
                .truncatedTo(ChronoUnit.MILLIS);
    }

    public static PluginVersion initial(String filename, String content) {
        return create(1, filename, content);
    }

    public static PluginVersion next(PluginVersion previous, String filename, String content) {
        Objects.requireNonNull(previous, "上一版本不能为空");
        return create(previous.revision + 1, filename, content);
    }

    public static PluginVersion restore(int revision,
                                        String filename,
                                        String content,
                                        String contentHash,
                                        Instant createdAt) {
        return new PluginVersion(revision, filename, content, contentHash, createdAt);
    }

    private static PluginVersion create(int revision, String filename, String content) {
        String normalizedContent = requireContent(content);
        return new PluginVersion(
                revision,
                filename,
                normalizedContent,
                sha256(normalizedContent),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 SHA-256", e);
        }
    }

    private static String requireFilename(String filename) {
        String value = requireText(filename, "文件名").trim();
        if (value.length() > MAX_FILENAME_LENGTH) {
            throw new DomainException(
                    "插件文件名超长（上限 " + MAX_FILENAME_LENGTH + " 字符）：" + value.length());
        }
        if (value.contains("/") || value.contains("\\")) {
            throw new DomainException("插件文件名不能包含路径分隔符：" + value);
        }
        return value;
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new DomainException("插件内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new DomainException(
                    "插件内容超长（上限 " + MAX_CONTENT_LENGTH + " 字符）：" + content.length());
        }
        return content;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }
}
