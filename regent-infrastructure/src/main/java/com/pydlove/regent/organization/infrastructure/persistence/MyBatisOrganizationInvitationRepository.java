package com.pydlove.regent.organization.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.Email;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationInvitation;
import com.pydlove.regent.organization.domain.model.OrganizationInvitationStatus;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.organization.domain.repository.OrganizationInvitationRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisOrganizationInvitationRepository implements OrganizationInvitationRepository {

    private final OrganizationInvitationMapper mapper;

    public MyBatisOrganizationInvitationRepository(OrganizationInvitationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public OrganizationInvitation save(OrganizationInvitation invitation) {
        OrganizationInvitationData data = toData(invitation);
        mapper.insert(data);
        invitation.assignIdentity(data.getId());
        return invitation;
    }

    @Override
    public void update(OrganizationInvitation invitation) {
        mapper.update(toData(invitation));
    }

    @Override
    public Optional<OrganizationInvitation> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(mapper.selectByTokenHash(tokenHash)).map(this::toDomain);
    }

    @Override
    public Optional<OrganizationInvitation> findPending(OrganizationId organizationId, Email email) {
        return Optional.ofNullable(
                        mapper.selectPending(organizationId.value(), email.value()))
                .map(this::toDomain);
    }

    @Override
    public List<OrganizationInvitation> findByOrganization(OrganizationId organizationId) {
        return mapper.selectByOrganization(organizationId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    private OrganizationInvitationData toData(OrganizationInvitation invitation) {
        OrganizationInvitationData data = new OrganizationInvitationData();
        data.setId(invitation.getId());
        data.setOrganizationId(invitation.getOrganizationId().value());
        data.setEmail(invitation.getEmail().value());
        data.setRoleCode(invitation.getRole().name());
        data.setTokenHash(invitation.getTokenHash());
        data.setStatus(invitation.getStatus().name());
        data.setExpiresAt(toDb(invitation.getExpiresAt()));
        data.setAcceptedAt(toDb(invitation.getAcceptedAt()));
        data.setAcceptedBy(invitation.getAcceptedBy() == null
                ? null : invitation.getAcceptedBy().value());
        data.setCreatedBy(invitation.getCreatedBy().value());
        data.setCreatedAt(toDb(invitation.getCreatedAt()));
        return data;
    }

    private OrganizationInvitation toDomain(OrganizationInvitationData data) {
        return OrganizationInvitation.restore(
                data.getId(),
                OrganizationId.of(data.getOrganizationId()),
                Email.of(data.getEmail()),
                OrganizationRole.valueOf(data.getRoleCode()),
                data.getTokenHash(),
                OrganizationInvitationStatus.parse(data.getStatus()),
                toInstant(data.getExpiresAt()),
                toInstant(data.getAcceptedAt()),
                data.getAcceptedBy() == null ? null : UserId.of(data.getAcceptedBy()),
                UserId.of(data.getCreatedBy()),
                toInstant(data.getCreatedAt()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
