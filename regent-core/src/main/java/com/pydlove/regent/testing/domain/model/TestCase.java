package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Web / 接口测试用例。
 *
 * <p>用例由「一条 shell 命令」和「可选的上传脚本」组成。命令在项目目录执行，
 * 上传脚本先还原到本次运行自己的临时目录，再通过 {@code REGENT_TEST_FILE} 暴露给命令。
 * 平台不绑定 Playwright、Newman、Pytest 等具体框架；团队已经在项目里用哪套，
 * 就在这里写哪套命令。
 */
public class TestCase {

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_DESCRIPTION_LENGTH = 1_024;
    private static final int MAX_COMMAND_LENGTH = 4_096;
    private static final int MAX_FILENAME_LENGTH = 255;
    public static final int MAX_SCRIPT_LENGTH = 512 * 1024;

    private TestCaseId id;
    private final ProjectId projectId;
    private String name;
    private TestCaseType type;
    private String description;
    private String command;
    private String scriptFilename;
    private String scriptContent;
    private String scriptHash;
    private boolean enabled;
    private final Instant createdAt;
    private Instant updatedAt;

    private TestCase(TestCaseId id,
                     ProjectId projectId,
                     String name,
                     TestCaseType type,
                     String description,
                     String command,
                     String scriptFilename,
                     String scriptContent,
                     String scriptHash,
                     boolean enabled,
                     Instant createdAt,
                     Instant updatedAt) {
        this.id = id;
        this.projectId = requireNotNull(projectId, "所属项目");
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
        apply(name, type, description, command, scriptFilename, scriptContent, scriptHash, enabled);
    }

    public static TestCase create(ProjectId projectId,
                                  String name,
                                  TestCaseType type,
                                  String description,
                                  String command,
                                  String scriptFilename,
                                  String scriptContent,
                                  boolean enabled) {
        Instant now = now();
        return new TestCase(null, projectId, name, type, description, command,
                scriptFilename, scriptContent, null, enabled, now, now);
    }

    public static TestCase restore(TestCaseId id,
                                   ProjectId projectId,
                                   String name,
                                   TestCaseType type,
                                   String description,
                                   String command,
                                   String scriptFilename,
                                   String scriptContent,
                                   String scriptHash,
                                   boolean enabled,
                                   Instant createdAt,
                                   Instant updatedAt) {
        return new TestCase(requireNotNull(id, "测试用例 ID"), projectId, name, type, description,
                command, scriptFilename, scriptContent, scriptHash, enabled, createdAt, updatedAt);
    }

    public void update(String name,
                       TestCaseType type,
                       String description,
                       String command,
                       String scriptFilename,
                       String scriptContent,
                       boolean enabled) {
        apply(name, type, description, command, scriptFilename, scriptContent, null, enabled);
        this.updatedAt = now();
    }

    public void changeEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        this.updatedAt = now();
    }

    public void assignIdentity(TestCaseId assigned) {
        if (id != null) {
            throw new DomainException("测试用例已有 ID，不能重复分配：" + id);
        }
        this.id = requireNotNull(assigned, "测试用例 ID");
    }

    public TestCaseBinding bind(Instant boundAt) {
        if (!enabled) {
            throw new DomainException("测试用例已停用，不能绑定任务：" + name);
        }
        return snapshot(boundAt);
    }

    /** 手动执行也使用同一份不可变快照，但不建立任务绑定关系。 */
    public TestCaseBinding snapshot(Instant at) {
        if (id == null) {
            throw new DomainException("测试用例尚未持久化，不能执行");
        }
        return new TestCaseBinding(
                id,
                name,
                type,
                command,
                scriptFilename,
                scriptContent,
                scriptHash,
                enabled,
                false,
                requireNotNull(at, "快照时间"));
    }

    private void apply(String name,
                       TestCaseType type,
                       String description,
                       String command,
                       String scriptFilename,
                       String scriptContent,
                       String scriptHash,
                       boolean enabled) {
        this.name = requireName(name);
        this.type = requireNotNull(type, "测试类型");
        this.description = optionalText(description, MAX_DESCRIPTION_LENGTH, "说明");
        this.command = optionalText(command, MAX_COMMAND_LENGTH, "执行命令");
        applyScript(scriptFilename, scriptContent, scriptHash);
        if (this.command == null && this.scriptContent == null) {
            throw new DomainException("测试用例至少要提供执行命令或上传脚本");
        }
        this.enabled = enabled;
    }

    private void applyScript(String filename, String content, String hash) {
        if (filename == null || filename.isBlank()) {
            if (content != null && !content.isBlank()) {
                throw new DomainException("上传脚本缺少文件名");
            }
            this.scriptFilename = null;
            this.scriptContent = null;
            this.scriptHash = null;
            return;
        }
        String normalizedFilename = requireFilename(filename);
        if (content == null || content.isBlank()) {
            throw new DomainException("上传脚本后内容不能为空");
        }
        if (content.length() > MAX_SCRIPT_LENGTH) {
            throw new DomainException(
                    "测试脚本超长（上限 " + MAX_SCRIPT_LENGTH + " 字符）：" + content.length());
        }
        String actualHash = sha256(content);
        if (hash != null && !hash.equals(actualHash)) {
            throw new DomainException("测试脚本摘要与内容不一致");
        }
        this.scriptFilename = normalizedFilename;
        this.scriptContent = content;
        this.scriptHash = actualHash;
    }

    private static String requireName(String name) {
        String value = requireText(name, "测试用例名称").trim();
        if (value.length() > MAX_NAME_LENGTH) {
            throw new DomainException(
                    "测试用例名称超长（上限 " + MAX_NAME_LENGTH + " 字符）：" + value.length());
        }
        return value;
    }

    private static String requireFilename(String filename) {
        String value = requireText(filename, "脚本文件名").trim();
        if (value.length() > MAX_FILENAME_LENGTH) {
            throw new DomainException(
                    "脚本文件名超长（上限 " + MAX_FILENAME_LENGTH + " 字符）：" + value.length());
        }
        if (value.contains("/") || value.contains("\\")) {
            throw new DomainException("脚本文件名不能包含路径分隔符：" + value);
        }
        return value;
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new DomainException(field + "超长（上限 " + max + " 字符）：" + normalized.length());
        }
        return normalized;
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 SHA-256", e);
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    public TestCaseId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public TestCaseType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getCommand() {
        return command;
    }

    public String getScriptFilename() {
        return scriptFilename;
    }

    public String getScriptContent() {
        return scriptContent;
    }

    public String getScriptHash() {
        return scriptHash;
    }

    public boolean hasScript() {
        return scriptContent != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
