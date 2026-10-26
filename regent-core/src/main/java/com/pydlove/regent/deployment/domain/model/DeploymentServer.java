package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 可复用的部署服务器。具体部署目录和脚本属于部署配置；这里只保存一个可复用的
 * 默认远程目录，用来在新建部署时预填 {@code REGENT_DEPLOY_DIRECTORY}。
 *
 * <p>一台服务器可以归属多个项目。服务器是连接信息的事实源，项目关联只表达哪些项目
 * 可以在新建部署时选择它；部署配置自身仍只归属一个项目。
 *
 * <p>SSH 密码同样不在聚合里，由独立凭据端口按服务器 ID 加密保存。
 */
public class DeploymentServer {

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_HOST_LENGTH = 255;
    private static final int MAX_USERNAME_LENGTH = 32;
    private static final int MAX_REMOTE_DIRECTORY_LENGTH = 512;

    private DeploymentServerId id;
    private Set<ProjectId> projectIds;
    private String name;
    private String host;
    private int port;
    private String username;
    private DeploymentAuthType authType;
    private String defaultRemoteDirectory;
    private final Instant createdAt;
    private Instant updatedAt;

    private DeploymentServer(DeploymentServerId id,
                             Collection<ProjectId> projectIds,
                             String name,
                             String host,
                             int port,
                             String username,
                             DeploymentAuthType authType,
                             String defaultRemoteDirectory,
                             Instant createdAt,
                             Instant updatedAt) {
        this.id = id;
        this.projectIds = requireProjectIds(projectIds);
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
        apply(name, host, port, username, authType, defaultRemoteDirectory);
    }

    public static DeploymentServer create(ProjectId projectId,
                                          String name,
                                          String host,
                                          int port,
                                          String username,
                                          DeploymentAuthType authType) {
        return create(Collections.singletonList(projectId), name, host, port, username,
                authType, null);
    }

    public static DeploymentServer create(List<ProjectId> projectIds,
                                          String name,
                                          String host,
                                          int port,
                                          String username,
                                          DeploymentAuthType authType) {
        return create(projectIds, name, host, port, username, authType, null);
    }

    public static DeploymentServer create(ProjectId projectId,
                                          String name,
                                          String host,
                                          int port,
                                          String username,
                                          DeploymentAuthType authType,
                                          String defaultRemoteDirectory) {
        return create(Collections.singletonList(projectId), name, host, port, username, authType,
                defaultRemoteDirectory);
    }

    public static DeploymentServer create(List<ProjectId> projectIds,
                                          String name,
                                          String host,
                                          int port,
                                          String username,
                                          DeploymentAuthType authType,
                                          String defaultRemoteDirectory) {
        Instant now = now();
        return new DeploymentServer(null, projectIds, name, host, port, username, authType,
                defaultRemoteDirectory, now, now);
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
        return restore(id, Collections.singletonList(projectId), name, host, port, username,
                authType, null, createdAt, updatedAt);
    }

    public static DeploymentServer restore(DeploymentServerId id,
                                           List<ProjectId> projectIds,
                                           String name,
                                           String host,
                                           int port,
                                           String username,
                                           DeploymentAuthType authType,
                                           Instant createdAt,
                                           Instant updatedAt) {
        return restore(id, projectIds, name, host, port, username, authType, null,
                createdAt, updatedAt);
    }

    public static DeploymentServer restore(DeploymentServerId id,
                                           ProjectId projectId,
                                           String name,
                                           String host,
                                           int port,
                                           String username,
                                           DeploymentAuthType authType,
                                           String defaultRemoteDirectory,
                                           Instant createdAt,
                                           Instant updatedAt) {
        return restore(id, Collections.singletonList(projectId), name, host, port, username, authType,
                defaultRemoteDirectory, createdAt, updatedAt);
    }

    public static DeploymentServer restore(DeploymentServerId id,
                                           List<ProjectId> projectIds,
                                           String name,
                                           String host,
                                           int port,
                                           String username,
                                           DeploymentAuthType authType,
                                           String defaultRemoteDirectory,
                                           Instant createdAt,
                                           Instant updatedAt) {
        return new DeploymentServer(requireNotNull(id, "部署服务器 ID"), projectIds, name,
                host, port, username, authType, defaultRemoteDirectory, createdAt, updatedAt);
    }

    public void update(String name,
                       String host,
                       int port,
                       String username,
                       DeploymentAuthType authType) {
        update(projectIds, name, host, port, username, authType, defaultRemoteDirectory);
    }

    public void update(ProjectId projectId,
                       String name,
                       String host,
                       int port,
                       String username,
                       DeploymentAuthType authType) {
        update(Collections.singletonList(projectId), name, host, port, username, authType,
                defaultRemoteDirectory);
    }

    public void update(String name,
                       String host,
                       int port,
                       String username,
                       DeploymentAuthType authType,
                       String defaultRemoteDirectory) {
        update(projectIds, name, host, port, username, authType, defaultRemoteDirectory);
    }

    public void update(Collection<ProjectId> projectIds,
                       String name,
                       String host,
                       int port,
                       String username,
                       DeploymentAuthType authType,
                       String defaultRemoteDirectory) {
        this.projectIds = requireProjectIds(projectIds);
        apply(name, host, port, username, authType, defaultRemoteDirectory);
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
        return new DeploymentServerSnapshot(id, name, host, port, username, authType);
    }

    private void apply(String name,
                       String host,
                       int port,
                       String username,
                       DeploymentAuthType authType,
                       String defaultRemoteDirectory) {
        this.name = requireName(name);
        this.host = requireHost(host);
        this.port = requirePort(port);
        this.username = requireUsername(username);
        this.authType = requireNotNull(authType, "认证方式");
        this.defaultRemoteDirectory = optionalRemoteDirectory(defaultRemoteDirectory);
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

    private static Set<ProjectId> requireProjectIds(Collection<ProjectId> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            throw new DomainException("请至少选择一个服务器归属项目");
        }
        Set<ProjectId> normalized = new LinkedHashSet<>();
        for (ProjectId projectId : projectIds) {
            ProjectId value = requireNotNull(projectId, "归属项目");
            if (!normalized.add(value)) {
                throw new DomainException("服务器归属项目不能重复：" + value.value());
            }
        }
        return normalized;
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

    /** 空值合法：不是每台服务器都承担部署工作。非空时与部署远程目录使用同一套安全规则。 */
    private static String optionalRemoteDirectory(String remoteDirectory) {
        if (remoteDirectory == null || remoteDirectory.isBlank()) {
            return null;
        }
        String value = remoteDirectory.trim();
        if (value.length() > MAX_REMOTE_DIRECTORY_LENGTH) {
            throw new DomainException("默认远程目录超长（上限 512 字符）");
        }
        if (!value.equals("~") && !value.startsWith("/") && !value.startsWith("~/")) {
            throw new DomainException("默认远程目录必须使用 ~ 或绝对路径：" + value);
        }
        if (!value.matches("[A-Za-z0-9._~/-]+")) {
            throw new DomainException("默认远程目录只能包含字母、数字、点、下划线、斜杠和 ~：" + value);
        }
        for (String segment : value.split("/")) {
            if ("..".equals(segment)) {
                throw new DomainException("默认远程目录不能包含 .. 路径段：" + value);
            }
        }
        return value;
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

    public List<ProjectId> getProjectIds() {
        return List.copyOf(projectIds);
    }

    public boolean belongsTo(ProjectId projectId) {
        return projectIds.contains(projectId);
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

    public String getDefaultRemoteDirectory() {
        return defaultRemoteDirectory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
