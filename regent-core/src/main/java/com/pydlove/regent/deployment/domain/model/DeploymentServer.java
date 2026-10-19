package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 可复用的部署服务器。远程目录和脚本属于部署配置，不在这里。
 *
 * <p>SSH 密码同样不在聚合里，由独立凭据端口按服务器 ID 加密保存。
 */
public class DeploymentServer {

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_HOST_LENGTH = 255;
    private static final int MAX_USERNAME_LENGTH = 32;

    private DeploymentServerId id;
    private final ProjectId projectId;
    private String name;
    private String host;
    private int port;
    private String username;
    private DeploymentAuthType authType;
    private final Instant createdAt;
    private Instant updatedAt;

    private DeploymentServer(DeploymentServerId id,
                             ProjectId projectId,
                             String name,
                             String host,
                             int port,
                             String username,
                             DeploymentAuthType authType,
                             Instant createdAt,
                             Instant updatedAt) {
        this.id = id;
        this.projectId = requireNotNull(projectId, "归属项目");
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
        apply(name, host, port, username, authType);
    }

    public static DeploymentServer create(ProjectId projectId,
                                          String name,
                                          String host,
                                          int port,
                                          String username,
                                          DeploymentAuthType authType) {
        Instant now = now();
        return new DeploymentServer(null, projectId, name, host, port, username, authType, now, now);
    }

    public static DeploymentServer restore(DeploymentServerId id,
                                           ProjectId projectId,
                                           String name,
                                           String host,
                                           int port,
                                           String username,
                                           DeploymentAuthType authType,
                                           Instant createdAt,
                                           Instant updatedAt) {
        return new DeploymentServer(requireNotNull(id, "部署服务器 ID"), projectId, name,
                host, port, username, authType, createdAt, updatedAt);
    }

    public void update(String name,
                       String host,
                       int port,
                       String username,
                       DeploymentAuthType authType) {
        apply(name, host, port, username, authType);
        this.updatedAt = now();
    }

    public void assignIdentity(DeploymentServerId assigned) {
        if (id != null) {
            throw new DomainException("部署服务器已有 ID，不能重复分配：" + id);
        }
        this.id = requireNotNull(assigned, "部署服务器 ID");
    }

    public DeploymentServerSnapshot snapshot() {
        if (id == null) {
            throw new DomainException("部署服务器尚未持久化，不能用于执行");
        }
        return new DeploymentServerSnapshot(id, projectId, name, host, port, username, authType);
    }

    private void apply(String name,
                       String host,
                       int port,
                       String username,
                       DeploymentAuthType authType) {
        this.name = requireName(name);
        this.host = requireHost(host);
        this.port = requirePort(port);
        this.username = requireUsername(username);
        this.authType = requireNotNull(authType, "认证方式");
    }

    private static String requireName(String name) {
        String value = requireText(name, "服务器名称").trim();
        if (value.length() > MAX_NAME_LENGTH) {
            throw new DomainException(
                    "服务器名称超长（上限 " + MAX_NAME_LENGTH + " 字符）：" + value.length());
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

    private static int requirePort(int port) {
        if (port < 1 || port > 65535) {
            throw new DomainException("SSH 端口必须在 1–65535 之间：" + port);
        }
        return port;
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

    public DeploymentServerId getId() {
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
