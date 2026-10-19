package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 一次部署执行记录。配置在入队时快照，编辑部署不会改写历史。
 * 完整日志在文件存储里，摘要只保留到人能快速判断「这次成没成」的长度。
 */
public class DeploymentRun {

    private static final int MAX_SUMMARY_LENGTH = 1024;

    private DeploymentRunId id;
    private final DeploymentId deploymentId;
    private final ProjectId projectId;
    private final String deploymentName;
    private final String host;
    private final int port;
    private final String username;
    private final DeploymentAuthType authType;
    private final String remoteDirectory;
    private final String scriptFilename;
    private final String scriptContent;
    private final String scriptHash;
    private final DeploymentExecutionMode executionMode;
    private final String projectScriptPath;
    private DeploymentRunStatus status;
    private Integer exitCode;
    private String summary;
    private final Instant queuedAt;
    private Instant startedAt;
    private Instant finishedAt;
    private final Instant createdAt;

    private DeploymentRun(DeploymentId deploymentId,
                          ProjectId projectId,
                          String deploymentName,
                          String host,
                          int port,
                          String username,
                          DeploymentAuthType authType,
                          String remoteDirectory,
                          String scriptFilename,
                          String scriptContent,
                          String scriptHash,
                          DeploymentExecutionMode executionMode,
                          String projectScriptPath,
                          DeploymentRunStatus status,
                          Integer exitCode,
                          String summary,
                          Instant queuedAt,
                          Instant startedAt,
                          Instant finishedAt,
                          Instant createdAt) {
        this.deploymentId = requireNotNull(deploymentId, "部署 ID");
        this.projectId = requireNotNull(projectId, "归属项目");
        this.deploymentName = requireText(deploymentName, "部署名称");
        this.host = requireText(host, "服务器地址");
        if (port < 1 || port > 65535) {
            throw new DomainException("SSH 端口必须在 1–65535 之间：" + port);
        }
        this.port = port;
        this.username = requireText(username, "登录用户");
        this.authType = requireNotNull(authType, "认证方式");
        this.remoteDirectory = requireText(remoteDirectory, "远程目录");
        this.scriptFilename = requireText(scriptFilename, "脚本文件名");
        this.scriptContent = requireText(scriptContent, "部署脚本");
        this.scriptHash = requireText(scriptHash, "脚本摘要");
        this.executionMode = requireNotNull(executionMode, "部署执行方式");
        this.projectScriptPath = executionMode == DeploymentExecutionMode.PROJECT_SCRIPT
                ? requireText(projectScriptPath, "项目脚本路径")
                : null;
        this.status = requireNotNull(status, "执行状态");
        this.exitCode = exitCode;
        this.summary = truncateSummary(summary);
        this.queuedAt = requireNotNull(queuedAt, "入队时间");
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.createdAt = requireNotNull(createdAt, "创建时间");
    }

    public static DeploymentRun queue(DeploymentSnapshot snapshot,
                                      DeploymentServerSnapshot server) {
        if (!snapshot.projectId().equals(server.projectId())) {
            throw new DomainException("部署与服务器不属于同一项目");
        }
        Instant now = now();
        return new DeploymentRun(
                snapshot.deploymentId(),
                snapshot.projectId(),
                snapshot.name(),
                server.host(),
                server.port(),
                server.username(),
                server.authType(),
                snapshot.remoteDirectory(),
                snapshot.scriptFilename(),
                snapshot.scriptContent(),
                snapshot.scriptHash(),
                snapshot.executionMode(),
                snapshot.projectScriptPath(),
                DeploymentRunStatus.QUEUED,
                null,
                null,
                now,
                null,
                null,
                now);
    }

    public static DeploymentRun restore(DeploymentRunId id,
                                        DeploymentId deploymentId,
                                        ProjectId projectId,
                                        String deploymentName,
                                        String host,
                                        int port,
                                        String username,
                                        DeploymentAuthType authType,
                                        String remoteDirectory,
                                        String scriptFilename,
                                        String scriptContent,
                                        String scriptHash,
                                        DeploymentExecutionMode executionMode,
                                        String projectScriptPath,
                                        DeploymentRunStatus status,
                                        Integer exitCode,
                                        String summary,
                                        Instant queuedAt,
                                        Instant startedAt,
                                        Instant finishedAt,
                                        Instant createdAt) {
        DeploymentRun run = new DeploymentRun(deploymentId, projectId, deploymentName, host, port,
                username, authType, remoteDirectory, scriptFilename, scriptContent, scriptHash,
                executionMode, projectScriptPath, status, exitCode, summary,
                queuedAt, startedAt, finishedAt, createdAt);
        run.id = requireNotNull(id, "执行记录 ID");
        return run;
    }

    public void begin() {
        requireStatus(DeploymentRunStatus.QUEUED, "只有排队中的部署可以开始");
        this.status = DeploymentRunStatus.RUNNING;
        this.startedAt = now();
    }

    public void finish(DeploymentExecutionResult result) {
        requireStatus(DeploymentRunStatus.RUNNING, "只有执行中的部署可以写入结论");
        this.status = result.status();
        this.exitCode = result.exitCode();
        this.summary = truncateSummary(result.summary());
        this.finishedAt = now();
    }

    public void interrupt(String reason) {
        if (status.isFinished()) {
            return;
        }
        this.status = DeploymentRunStatus.INTERRUPTED;
        this.summary = truncateSummary(reason);
        this.finishedAt = now();
    }

    public void assignIdentity(DeploymentRunId assigned) {
        if (id != null) {
            throw new DomainException("部署执行记录已有 ID，不能重复分配：" + id);
        }
        this.id = requireNotNull(assigned, "执行记录 ID");
    }

    private void requireStatus(DeploymentRunStatus expected, String message) {
        if (status != expected) {
            throw new DomainException(message + "，当前状态：" + status);
        }
    }

    private static String truncateSummary(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= MAX_SUMMARY_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SUMMARY_LENGTH) + "…";
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

    public DeploymentRunId getId() {
        return id;
    }

    public DeploymentId getDeploymentId() {
        return deploymentId;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public String getDeploymentName() {
        return deploymentName;
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

    public DeploymentExecutionMode getExecutionMode() {
        return executionMode;
    }

    public String getProjectScriptPath() {
        return projectScriptPath;
    }

    public DeploymentRunStatus getStatus() {
        return status;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
