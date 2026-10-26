package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/** 项目级产品版本：承接需求池中的需求规划。 */
public class ProductVersion {

    private static final Pattern VERSION_NO =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}");

    private ProductVersionId id;
    private final ProjectId projectId;
    private String versionNo;
    private String name;
    private String description;
    private ProductVersionStatus status;
    private final UserId createdBy;
    private final Instant createdAt;
    private Instant updatedAt;

    private ProductVersion(ProductVersionId id,
                           ProjectId projectId,
                           String versionNo,
                           String name,
                           String description,
                           ProductVersionStatus status,
                           UserId createdBy,
                           Instant createdAt,
                           Instant updatedAt) {
        this.id = id;
        this.projectId = requireNotNull(projectId, "所属项目");
        this.versionNo = requireVersionNo(versionNo);
        this.name = requireText(name, "版本名称", 160);
        this.description = optionalText(description, 8000);
        this.status = requireNotNull(status, "版本状态");
        this.createdBy = requireNotNull(createdBy, "创建人");
        this.createdAt = requireNotNull(createdAt, "创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "更新时间").truncatedTo(ChronoUnit.MILLIS);
    }

    public static ProductVersion create(ProjectId projectId,
                                        String versionNo,
                                        String name,
                                        String description,
                                        UserId createdBy) {
        Instant now = now();
        return new ProductVersion(
                null, projectId, versionNo, name, description,
                ProductVersionStatus.PLANNING, createdBy, now, now);
    }

    public static ProductVersion restore(ProductVersionId id,
                                         ProjectId projectId,
                                         String versionNo,
                                         String name,
                                         String description,
                                         ProductVersionStatus status,
                                         UserId createdBy,
                                         Instant createdAt,
                                         Instant updatedAt) {
        return new ProductVersion(
                id, projectId, versionNo, name, description, status, createdBy,
                createdAt, updatedAt);
    }

    public void update(String name, String description) {
        requirePlanning("只有规划中的产品版本可以修改");
        this.name = requireText(name, "版本名称", 160);
        this.description = optionalText(description, 8000);
        touch();
    }

    public void close() {
        if (status == ProductVersionStatus.CLOSED) return;
        status = ProductVersionStatus.CLOSED;
        touch();
    }

    public void assignIdentity(ProductVersionId assigned) {
        if (id != null) {
            throw new DomainException("产品版本已有 ID，不能重复分配");
        }
        id = requireNotNull(assigned, "产品版本 ID");
    }

    private void requirePlanning(String message) {
        if (status != ProductVersionStatus.PLANNING) {
            throw new DomainException(message + "，当前状态：" + status);
        }
    }

    private void touch() {
        updatedAt = now();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String requireVersionNo(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("产品版本号不能为空");
        }
        String normalized = value.trim();
        if (!VERSION_NO.matcher(normalized).matches()) {
            throw new DomainException("产品版本号只能包含字母、数字、点、下划线、加号和连字符");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
        String text = value.trim();
        if (text.length() > max) {
            throw new DomainException(field + "不能超过 " + max + " 个字符");
        }
        return text;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > max) {
            throw new DomainException("文本不能超过 " + max + " 个字符");
        }
        return text;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }

    public ProductVersionId getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public String getVersionNo() {
        return versionNo;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProductVersionStatus getStatus() {
        return status;
    }

    public UserId getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
