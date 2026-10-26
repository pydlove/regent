package com.pydlove.regent.organization.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

/** 组织是项目之上的业务租户。 */
public class Organization {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_-]{1,63}");
    private static final int MAX_NAME = 128;
    private static final int MAX_DESCRIPTION = 512;

    private OrganizationId id;
    private final String code;
    private String name;
    private String description;
    private OrganizationStatus status;
    private final UserId createdBy;
    private final Instant createdAt;
    private Instant updatedAt;

    private Organization(OrganizationId id,
                         String code,
                         String name,
                         String description,
                         OrganizationStatus status,
                         UserId createdBy,
                         Instant createdAt,
                         Instant updatedAt) {
        this.id = id;
        this.code = requireCode(code);
        this.name = requireName(name);
        this.description = optionalText(description, MAX_DESCRIPTION, "组织描述");
        this.status = status == null ? OrganizationStatus.ACTIVE : status;
        this.createdBy = requireNotNull(createdBy, "组织创建人");
        this.createdAt = requireNotNull(createdAt, "组织创建时间").truncatedTo(ChronoUnit.MILLIS);
        this.updatedAt = requireNotNull(updatedAt, "组织更新时间").truncatedTo(ChronoUnit.MILLIS);
    }

    public static Organization create(String code,
                                      String name,
                                      String description,
                                      UserId createdBy) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        return new Organization(null, code, name, description, OrganizationStatus.ACTIVE,
                createdBy, now, now);
    }

    public static Organization restore(OrganizationId id,
                                       String code,
                                       String name,
                                       String description,
                                       OrganizationStatus status,
                                       UserId createdBy,
                                       Instant createdAt,
                                       Instant updatedAt) {
        return new Organization(requireNotNull(id, "组织 ID"), code, name, description, status,
                createdBy, createdAt, updatedAt);
    }

    public void updateDetails(String name, String description) {
        this.name = requireName(name);
        this.description = optionalText(description, MAX_DESCRIPTION, "组织描述");
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public void assignIdentity(OrganizationId assigned) {
        if (id != null) {
            throw new DomainException("组织已有 ID，不能重复分配");
        }
        this.id = requireNotNull(assigned, "组织 ID");
    }

    public OrganizationId getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public OrganizationStatus getStatus() {
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

    private static String requireCode(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("组织标识不能为空");
        }
        String normalized = value.trim();
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new DomainException("组织标识需为 2-64 位小写字母、数字、下划线或连字符，且以字母开头");
        }
        return normalized;
    }

    private static String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException("组织名称不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_NAME) {
            throw new DomainException("组织名称不能超过 " + MAX_NAME + " 个字符");
        }
        return normalized;
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new DomainException(field + "不能超过 " + max + " 个字符");
        }
        return normalized;
    }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) throw new DomainException(field + "不能为空");
        return value;
    }
}
