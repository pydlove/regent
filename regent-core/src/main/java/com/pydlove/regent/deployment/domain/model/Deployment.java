package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * 部署配置聚合。
 *
 * <p>服务器地址、登录用户和脚本文本是配置；密码不是。密码认证的密码保存在独立凭据端口，
 * 由基础设施加密后落库，部署聚合和执行快照都不持有密码。
 */
public class Deployment {

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_HOST_LENGTH = 255;
    private static final int MAX_USERNAME_LENGTH = 32;
    private static final int MAX_REMOTE_DIRECTORY_LENGTH = 512;
    private static final int MAX_FILENAME_LENGTH = 255;
    public static final int MAX_SCRIPT_LENGTH = 512 * 1024;

    private DeploymentId id;
    private final ProjectId projectId;
    private String name;
    private String host;
    private int port;
    private String username;
    private DeploymentAuthType authType;
    private String remoteDirectory;
    private String scriptFilename;
    private String scriptContent;
    private String scriptHash;
    private final Instant createdAt;
    private Instant updatedAt;

    private Deployment(DeploymentId id,
                       ProjectId projectId,
                       String name,
                       String host,
                       int port,
                       String username,
                       DeploymentAuthType authType,
                       String remoteDirectory,
                       String scriptFilename,
                       String scriptContent,
                       String scriptHash,
                       Instant createdAt,
                       Instant updatedAt) {
        this.id = id;
        this.projectId = requireNotNull(projectId, "归属项目");
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
        apply(name, host, port, username, authType, remoteDirectory,
                scriptFilename, scriptContent, scriptHash);
    }

    public static Deployment create(ProjectId projectId,
                                    String name,
                                    String host,
                                    int port,
                                    String username,
                                    DeploymentAuthType authType,
                                    String remoteDirectory,
                                    String scriptFilename,
                                    String scriptContent) {
        Instant now = now();
        return new Deployment(null, projectId, name, host, port, username, authType, remoteDirectory,
                scriptFilename, scriptContent, null, now, now);
    }

    public static Deployment restore(DeploymentId id,
                                     ProjectId projectId,
                                     String name,
                                     String host,
                                     int port,
                                     String username,
                                     DeploymentAuthType authType,
                                     String remoteDirectory,
                                     String scriptFilename,
                                     String scriptContent,
                                     String scriptHash,
                                     Instant createdAt,
                                     Instant updatedAt) {
        return new Deployment(requireNotNull(id, "部署 ID"), projectId, name, host, port, username,
                authType, remoteDirectory, scriptFilename, scriptContent, scriptHash,
                createdAt, updatedAt);
    }

    public void update(String name,
                       String host,
                       int port,
                       String username,
                       DeploymentAuthType authType,
                       String remoteDirectory,
                       String scriptFilename,
                       String scriptContent) {
        apply(name, host, port, username, authType, remoteDirectory,
                scriptFilename, scriptContent, null);
        this.updatedAt = now();
    }

    public void assignIdentity(DeploymentId assigned) {
        if (id != null) {
            throw new DomainException("部署已有 ID，不能重复分配：" + id);
        }
        this.id = requireNotNull(assigned, "部署 ID");
    }

    public DeploymentSnapshot snapshot() {
        if (id == null) {
            throw new DomainException("部署尚未持久化，不能执行");
        }
        return new DeploymentSnapshot(id, projectId, name, host, port, username, authType,
                remoteDirectory, scriptFilename, scriptContent, scriptHash);
    }

    private void apply(String name,
                       String host,
                       int port,
                       String username,
                       DeploymentAuthType authType,
                       String remoteDirectory,
                       String scriptFilename,
                       String scriptContent,
                       String scriptHash) {
        this.name = requireName(name);
        this.host = requireHost(host);
        this.port = requirePort(port);
        this.username = requireUsername(username);
        this.authType = requireNotNull(authType, "认证方式");
        this.remoteDirectory = requireRemoteDirectory(remoteDirectory);
        applyScript(scriptFilename, scriptContent, scriptHash);
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

    private static String requireHost(String host) {
        String value = requireText(host, "服务器地址").trim();
        if (value.length() > MAX_HOST_LENGTH) {
            throw new DomainException(
                    "服务器地址超长（上限 " + MAX_HOST_LENGTH + " 字符）：" + value.length());
        }
        if (!value.matches("[A-Za-z0-9._:\\[\\]-]+")) {
            throw new DomainException("服务器地址格式不正确：" + value);
        }
        return value;
    }

    private static String requireUsername(String username) {
        String value = requireText(username, "登录用户").trim();
        if (value.length() > MAX_USERNAME_LENGTH
                || !value.matches("[A-Za-z0-9._-]+")) {
            throw new DomainException("SSH 用户名格式不正确：" + value);
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

    private static int requirePort(int port) {
        if (port < 1 || port > 65535) {
            throw new DomainException("SSH 端口必须在 1–65535 之间：" + port);
        }
        return port;
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

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public DeploymentAuthType getAuthType() {
        return authType;
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
