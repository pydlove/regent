package com.pydlove.regent.organization.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationAuditEntry;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.repository.OrganizationAuditRepository;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;

@Repository
public class MyBatisOrganizationAuditRepository implements OrganizationAuditRepository {

    private final OrganizationAuditMapper mapper;

    public MyBatisOrganizationAuditRepository(OrganizationAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OrganizationAuditEntry entry) {
        OrganizationAuditData data = new OrganizationAuditData();
        data.setOrganizationId(entry.organizationId().value());
        data.setActorId(entry.actorId().value());
        data.setAction(entry.action());
        data.setTargetType(entry.targetType());
        data.setTargetId(entry.targetId());
        data.setDetailJson(entry.detailJson());
        data.setCreatedAt(java.time.LocalDateTime.ofInstant(entry.createdAt(), ZoneOffset.UTC));
        mapper.insert(data);
    }

    @Override
    public List<OrganizationAuditEntry> findRecent(OrganizationId organizationId, int limit) {
        return mapper.selectRecent(organizationId.value(), limit).stream()
                .map(data -> new OrganizationAuditEntry(
                        data.getId(),
                        OrganizationId.of(data.getOrganizationId()),
                        UserId.of(data.getActorId()),
                        data.getAction(),
                        data.getTargetType(),
                        data.getTargetId(),
                        data.getDetailJson(),
                        data.getCreatedAt().toInstant(ZoneOffset.UTC)))
                .toList();
    }
}
