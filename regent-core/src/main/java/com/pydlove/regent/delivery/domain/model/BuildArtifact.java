package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 不可变构建制品。发布只允许在它之上逐级晋级。 */
public class BuildArtifact {

    private BuildArtifactId id;
    private final ProjectId projectId;
    private final String version;
    private final String gitCommit;
    private final String digest;
    private final long sizeBytes;
    private final String storageRef;
    private final UserId builtBy;
    private final Instant builtAt;

    private BuildArtifact(BuildArtifactId id,
                          ProjectId projectId,
                          String version,
                          String gitCommit,
                          String digest,
                          long sizeBytes,
                          String storageRef,
                          UserId builtBy,
                          Instant builtAt) {
        this.id = id;
        this.projectId = requireNotNull(projectId, "项目 ID");
        this.version = requireText(version, "版本", 64);
        this.gitCommit = requireText(gitCommit, "Git 提交", 64);
        this.digest = optionalText(digest, 128);
        this.sizeBytes = Math.max(0, sizeBytes);
        this.storageRef = optionalText(storageRef, 1000);
        this.builtBy = requireNotNull(builtBy, "构建人");
        this.builtAt = requireNotNull(builtAt, "构建时间").truncatedTo(ChronoUnit.MILLIS);
    }

    public static BuildArtifact create(ProjectId projectId,
                                       String version,
                                       String gitCommit,
                                       String digest,
                                       long sizeBytes,
                                       String storageRef,
                                       UserId builtBy) {
        return new BuildArtifact(null, projectId, version, gitCommit, digest, sizeBytes,
                storageRef, builtBy, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    public static BuildArtifact restore(BuildArtifactId id,
                                        ProjectId projectId,
                                        String version,
                                        String gitCommit,
                                        String digest,
                                        long sizeBytes,
                                        String storageRef,
                                        UserId builtBy,
                                        Instant builtAt) {
        return new BuildArtifact(id, projectId, version, gitCommit, digest, sizeBytes,
                storageRef, builtBy, builtAt);
    }

    public void assignIdentity(BuildArtifactId assigned) {
        if (id != null) throw new DomainException("制品已有 ID，不能重复分配");
        id = requireNotNull(assigned, "制品 ID");
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new DomainException(field + "不能为空");
        String text = value.trim();
        if (text.length() > max) throw new DomainException(field + "不能超过 " + max + " 个字符");
        return text;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > max) throw new DomainException("文本不能超过 " + max + " 个字符");
        return text;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) throw new DomainException(field + "不能为空");
        return value;
    }

    public BuildArtifactId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public String getVersion() {
        return version;
    }

    public String getGitCommit() {
        return gitCommit;
    }

    public String getDigest() {
        return digest;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageRef() {
        return storageRef;
    }

    public UserId getBuiltBy() {
        return builtBy;
    }

    public Instant getBuiltAt() {
        return builtAt;
    }
}
