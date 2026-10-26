package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 部署配置聚合。
 *
 * <p>服务器地址、登录用户和脚本文本是配置；密码不是。密码认证的密码保存在独立凭据端口，
 * 由基础设施加密后落库，部署聚合和执行快照都不持有密码。
 */
public class Deployment {

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_REMOTE_DIRECTORY_LENGTH = 512;
    private static final int MAX_SCRIPT_PATH_LENGTH = 512;
    private static final int MAX_FILENAME_LENGTH = 255;
    public static final int MAX_SCRIPT_LENGTH = 512 * 1024;

    private DeploymentId id;
    private ProjectId projectId;
    private Set<ProjectId> projectIds;
    private DeploymentServerId serverId;
    private String name;
    private String remoteDirectory;
    private String scriptFilename;
    private String scriptContent;
    private String scriptHash;
    private DeploymentExecutionMode executionMode;
    private String projectScriptPath;
    private final Instant createdAt;
    private Instant updatedAt;

    private Deployment(DeploymentId id,
                       Collection<ProjectId> projectIds,
                       DeploymentServerId serverId,
                       String name,
                       String remoteDirectory,
                       String scriptFilename,
                       String scriptContent,
                       String scriptHash,
                       DeploymentExecutionMode executionMode,
                       String projectScriptPath,
                       Instant createdAt,
                       Instant updatedAt) {
        this.id = id;
        applyProjectIds(projectIds);
        this.serverId = requireNotNull(serverId, "部署服务器");
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
        apply(projectIds, name, serverId, remoteDirectory, scriptFilename, scriptContent, scriptHash,
                executionMode, projectScriptPath);
    }

    public static Deployment create(ProjectId projectId,
                                    DeploymentServerId serverId,
                                    String name,
                                    String remoteDirectory,
                                    String scriptFilename,
                                    String scriptContent) {
        return create(List.of(projectId), serverId, name, remoteDirectory, scriptFilename, scriptContent,
                DeploymentExecutionMode.REMOTE_SCRIPT, null);
    }

    public static Deployment create(ProjectId projectId,
                                    DeploymentServerId serverId,
                                    String name,
                                    String remoteDirectory,
                                    String scriptFilename,
                                    String scriptContent,
                                    DeploymentExecutionMode executionMode,
                                    String projectScriptPath) {
        return create(List.of(projectId), serverId, name, remoteDirectory, scriptFilename,
                scriptContent, executionMode, projectScriptPath);
    }

    public static Deployment create(Collection<ProjectId> projectIds,
                                    DeploymentServerId serverId,
                                    String name,
                                    String remoteDirectory,
                                    String scriptFilename,
                                    String scriptContent) {
        return create(projectIds, serverId, name, remoteDirectory, scriptFilename, scriptContent,
                DeploymentExecutionMode.REMOTE_SCRIPT, null);
    }

    public static Deployment create(Collection<ProjectId> projectIds,
                                    DeploymentServerId serverId,
                                    String name,
                                    String remoteDirectory,
                                    String scriptFilename,
                                    String scriptContent,
                                    DeploymentExecutionMode executionMode,
                                    String projectScriptPath) {
        Instant now = now();
        return new Deployment(null, projectIds, serverId, name, remoteDirectory,
                scriptFilename, scriptContent, null, executionMode, projectScriptPath, now, now);
    }

    public static Deployment restore(DeploymentId id,
                                     ProjectId projectId,
                                     DeploymentServerId serverId,
                                     String name,
                                     String remoteDirectory,
                                     String scriptFilename,
                                     String scriptContent,
                                     String scriptHash,
                                     Instant createdAt,
                                     Instant updatedAt) {
        return restore(id, List.of(projectId), serverId, name, remoteDirectory, scriptFilename,
                scriptContent, scriptHash, DeploymentExecutionMode.REMOTE_SCRIPT, null,
                createdAt, updatedAt);
    }

    public static Deployment restore(DeploymentId id,
                                     ProjectId projectId,
                                     DeploymentServerId serverId,
                                     String name,
                                     String remoteDirectory,
                                     String scriptFilename,
                                     String scriptContent,
                                     String scriptHash,
                                     DeploymentExecutionMode executionMode,
                                     String projectScriptPath,
                                     Instant createdAt,
                                     Instant updatedAt) {
        return restore(id, List.of(projectId), serverId, name, remoteDirectory, scriptFilename,
                scriptContent, scriptHash, executionMode, projectScriptPath, createdAt, updatedAt);
    }

    public static Deployment restore(DeploymentId id,
                                     Collection<ProjectId> projectIds,
                                     DeploymentServerId serverId,
                                     String name,
                                     String remoteDirectory,
                                     String scriptFilename,
                                     String scriptContent,
                                     String scriptHash,
                                     DeploymentExecutionMode executionMode,
                                     String projectScriptPath,
                                     Instant createdAt,
                                     Instant updatedAt) {
        return new Deployment(requireNotNull(id, "部署 ID"), projectIds, serverId, name,
                remoteDirectory, scriptFilename, scriptContent, scriptHash, executionMode,
                projectScriptPath, createdAt, updatedAt);
    }

    public void update(String name,
                       DeploymentServerId serverId,
                       String remoteDirectory,
                       String scriptFilename,
                       String scriptContent) {
        update(name, serverId, remoteDirectory, scriptFilename, scriptContent,
                DeploymentExecutionMode.REMOTE_SCRIPT, null);
    }

    public void update(String name,
                       DeploymentServerId serverId,
                       String remoteDirectory,
                       String scriptFilename,
                       String scriptContent,
                       DeploymentExecutionMode executionMode,
                       String projectScriptPath) {
        apply(projectIds, name, serverId, remoteDirectory,
                scriptFilename, scriptContent, null, executionMode, projectScriptPath);
        this.updatedAt = now();
    }

    public void update(Collection<ProjectId> projectIds,
                       String name,
                       DeploymentServerId serverId,
                       String remoteDirectory,
                       String scriptFilename,
                       String scriptContent,
                       DeploymentExecutionMode executionMode,
                       String projectScriptPath) {
        applyProjectIds(projectIds);
        apply(projectIds, name, serverId, remoteDirectory,
                scriptFilename, scriptContent, null, executionMode, projectScriptPath);
        this.updatedAt = now();
    }

    public void assignIdentity(DeploymentId assigned) {
        if (id != null) {
            throw new DomainException("部署已有 ID，不能重复分配：" + id);
        }
        this.id = requireNotNull(assigned, "部署 ID");
    }

    public DeploymentSnapshot snapshot() {
        return snapshot(projectId);
    }

    public DeploymentSnapshot snapshot(ProjectId targetProjectId) {
        return snapshot(targetProjectId, executionMode, projectScriptPath);
    }

    public DeploymentSnapshot snapshot(ProjectId targetProjectId,
                                       DeploymentExecutionMode targetMode,
                                       String targetProjectScriptPath) {
        if (id == null) {
            throw new DomainException("部署尚未持久化，不能执行");
        }
        ProjectId executionProjectId = requireNotNull(targetProjectId, "本次执行项目");
        if (!belongsTo(executionProjectId)) {
            throw new DomainException("本次执行项目不属于该部署：" + executionProjectId.value());
        }
        return new DeploymentSnapshot(id, executionProjectId, serverId, name,
                remoteDirectory, scriptFilename, scriptContent, scriptHash,
                targetMode, targetProjectScriptPath);
    }

    private void apply(Collection<ProjectId> projectIds,
                       String name,
                       DeploymentServerId serverId,
                       String remoteDirectory,
                       String scriptFilename,
                       String scriptContent,
                       String scriptHash,
                       DeploymentExecutionMode executionMode,
                       String projectScriptPath) {
        applyProjectIds(projectIds);
        this.name = requireName(name);
        this.serverId = requireNotNull(serverId, "部署服务器");
        this.remoteDirectory = requireRemoteDirectory(remoteDirectory);
        applyScript(scriptFilename, scriptContent, scriptHash);
        this.executionMode = executionMode == null ? DeploymentExecutionMode.REMOTE_SCRIPT : executionMode;
        this.projectScriptPath = this.executionMode == DeploymentExecutionMode.PROJECT_SCRIPT
                ? requireProjectScriptPath(projectScriptPath)
                : null;
    }

    private void applyProjectIds(Collection<ProjectId> values) {
        if (values == null || values.isEmpty()) {
            throw new DomainException("请至少选择一个部署归属项目");
        }
        LinkedHashSet<ProjectId> normalized = new LinkedHashSet<>();
        for (ProjectId value : values) {
            normalized.add(requireNotNull(value, "归属项目"));
        }
        this.projectIds = normalized;
        this.projectId = normalized.iterator().next();
    }

    private void applyScript(String filename, String content, String hash) {
        String normalizedFilename = requireFilename(filename);
        String normalizedContent = requireText(content, "部署脚本");
        if (normalizedContent.length() > MAX_SCRIPT_LENGTH) {
            throw new DomainException(
                    "部署脚本超长（上限 " + MAX_SCRIPT_LENGTH + " 字符）：" + normalizedContent.length());
        }
        String actualHash = sha256(normalizedContent);
        if (hash != null && !hash.equals(actualHash)) {
            throw new DomainException("部署脚本摘要与内容不一致");
        }
        this.scriptFilename = normalizedFilename;
        this.scriptContent = normalizedContent;
        this.scriptHash = actualHash;
    }

    private static String requireName(String name) {
        String value = requireText(name, "部署名称").trim();
        if (value.length() > MAX_NAME_LENGTH) {
            throw new DomainException(
                    "部署名称超长（上限 " + MAX_NAME_LENGTH + " 字符）：" + value.length());
        }
        return value;
    }

    private static String requireRemoteDirectory(String remoteDirectory) {
        String value = requireText(remoteDirectory, "远程目录").trim();
        if (value.length() > MAX_REMOTE_DIRECTORY_LENGTH) {
            throw new DomainException(
                    "远程目录超长（上限 " + MAX_REMOTE_DIRECTORY_LENGTH + " 字符）：" + value.length());
        }
        if (!value.equals("~") && !value.startsWith("/") && !value.startsWith("~/")) {
            throw new DomainException("远程目录必须使用 ~ 或绝对路径：" + value);
        }
        if (!value.matches("[A-Za-z0-9._~/-]+")) {
            throw new DomainException("远程目录只能包含字母、数字、点、下划线、斜杠和 ~：" + value);
        }
        String[] segments = value.split("/");
        for (String segment : segments) {
            if ("..".equals(segment)) {
                throw new DomainException("远程目录不能包含 .. 路径段：" + value);
            }
        }
        return value;
    }

    private static String requireFilename(String filename) {
        String value = requireText(filename, "脚本文件名").trim();
        if (value.length() > MAX_FILENAME_LENGTH) {
            throw new DomainException(
                    "部署脚本文件名超长（上限 " + MAX_FILENAME_LENGTH + " 字符）：" + value.length());
        }
        if (value.contains("/") || value.contains("\\")) {
            throw new DomainException("部署脚本文件名不能包含路径分隔符：" + value);
        }
        return value;
    }

    private static String requireProjectScriptPath(String scriptPath) {
        String value = requireText(scriptPath, "项目脚本路径").trim();
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        if (value.length() > MAX_SCRIPT_PATH_LENGTH) {
            throw new DomainException(
                    "项目脚本路径超长（上限 " + MAX_SCRIPT_PATH_LENGTH + " 字符）：" + value.length());
        }
        if (value.startsWith("/") || value.contains("\\")) {
            throw new DomainException("项目脚本路径必须是项目内的相对路径：" + value);
        }
        for (String segment : value.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new DomainException("项目脚本路径不能包含空段、. 或 ..：" + value);
            }
        }
        return value;
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(content.getBytes(StandardCharsets.UTF_8)));
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

    public DeploymentId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public List<ProjectId> getProjectIds() {
        return List.copyOf(projectIds);
    }

    public boolean belongsTo(ProjectId projectId) {
        return projectIds.contains(projectId);
    }

    public DeploymentServerId getServerId() {
        return serverId;
    }

    public String getName() {
        return name;
    }

    public String getRemoteDirectory() {
        return remoteDirectory;
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

    public DeploymentExecutionMode getExecutionMode() {
        return executionMode;
    }

    public String getProjectScriptPath() {
        return projectScriptPath;
    }

    public int getScriptSize() {
        return scriptContent.getBytes(StandardCharsets.UTF_8).length;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
