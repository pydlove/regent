package com.pydlove.regent.organization.domain.model;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 一次性组织邀请。邀请令牌只保存 SHA-256。 */
public class OrganizationInvitation {

    private Long id;
    private final OrganizationId organizationId;
    private final Email email;
    private final OrganizationRole role;
    private final String tokenHash;
    private OrganizationInvitationStatus status;
    private final Instant expiresAt;
    private Instant acceptedAt;
    private UserId acceptedBy;
    private final UserId createdBy;
    private final Instant createdAt;

    private OrganizationInvitation(Long id,
                                   OrganizationId organizationId,
                                   Email email,
                                   OrganizationRole role,
                                   String tokenHash,
                                   OrganizationInvitationStatus status,
                                   Instant expiresAt,
                                   Instant acceptedAt,
                                   UserId acceptedBy,
                                   UserId createdBy,
                                   Instant createdAt) {
        this.id = id;
        this.organizationId = requireNotNull(organizationId, "组织 ID");
        this.email = requireNotNull(email, "邀请邮箱");
        this.role = requireNotNull(role, "组织角色");
        if (role == OrganizationRole.ORG_OWNER) {
            throw new DomainException("不能通过邀请授予组织所有者角色");
        }
        if (tokenHash == null || !tokenHash.matches("[0-9a-f]{64}")) {
            throw new DomainException("邀请令牌摘要不合法");
        }
        this.tokenHash = tokenHash;
        this.status = status == null ? OrganizationInvitationStatus.PENDING : status;
        this.expiresAt = requireNotNull(expiresAt, "邀请过期时间").truncatedTo(ChronoUnit.MILLIS);
        this.acceptedAt = acceptedAt;
        this.acceptedBy = acceptedBy;
        this.createdBy = requireNotNull(createdBy, "邀请创建人");
        this.createdAt = requireNotNull(createdAt, "邀请创建时间").truncatedTo(ChronoUnit.MILLIS);
    }

    public static OrganizationInvitation create(OrganizationId organizationId,
                                                Email email,
                                                OrganizationRole role,
                                                String tokenHash,
                                                Instant expiresAt,
                                                UserId createdBy) {
        return new OrganizationInvitation(null, organizationId, email, role, tokenHash,
                OrganizationInvitationStatus.PENDING, expiresAt, null, null, createdBy,
                Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    public static OrganizationInvitation restore(Long id,
                                                 OrganizationId organizationId,
                                                 Email email,
                                                 OrganizationRole role,
                                                 String tokenHash,
                                                 OrganizationInvitationStatus status,
                                                 Instant expiresAt,
                                                 Instant acceptedAt,
                                                 UserId acceptedBy,
                                                 UserId createdBy,
                                                 Instant createdAt) {
        return new OrganizationInvitation(id, organizationId, email, role, tokenHash, status,
                expiresAt, acceptedAt, acceptedBy, createdBy, createdAt);
    }

    public void requireUsable(Instant now) {
        if (status == OrganizationInvitationStatus.ACCEPTED) {
            throw new DomainException("邀请已被接受");
        }
        if (status == OrganizationInvitationStatus.REVOKED) {
            throw new DomainException("邀请已被撤销");
        }
        if (status == OrganizationInvitationStatus.EXPIRED || !expiresAt.isAfter(now)) {
            throw new DomainException("邀请已过期，请让管理员重新发送");
        }
    }

    public void accept(UserId userId, Instant now) {
        requireUsable(now);
        if (acceptedBy != null) {
            throw new DomainException("邀请已被接受");
        }
        this.status = OrganizationInvitationStatus.ACCEPTED;
        this.acceptedAt = now.truncatedTo(ChronoUnit.MILLIS);
        this.acceptedBy = requireNotNull(userId, "接受人");
    }

    public void revoke() {
        if (status == OrganizationInvitationStatus.ACCEPTED) {
            throw new DomainException("已接受的邀请不能撤销");
        }
        this.status = OrganizationInvitationStatus.REVOKED;
    }

    public void assignIdentity(long assigned) {
        if (id != null) throw new DomainException("邀请已有 ID");
        this.id = assigned;
    }

    public Long getId() { return id; }
    public OrganizationId getOrganizationId() { return organizationId; }
    public Email getEmail() { return email; }
    public OrganizationRole getRole() { return role; }
    public String getTokenHash() { return tokenHash; }
    public OrganizationInvitationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public UserId getAcceptedBy() { return acceptedBy; }
    public UserId getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }

    private static <T> T requireNotNull(T value, String field) {
        if (value == null) throw new DomainException(field + "不能为空");
        return value;
    }
}
