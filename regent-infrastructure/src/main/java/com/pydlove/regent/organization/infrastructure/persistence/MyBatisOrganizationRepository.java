package com.pydlove.regent.organization.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.Organization;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationStatus;
import com.pydlove.regent.organization.domain.repository.OrganizationRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisOrganizationRepository implements OrganizationRepository {

    private final OrganizationMapper mapper;

    public MyBatisOrganizationRepository(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Organization save(Organization organization) {
        OrganizationData data = toData(organization);
        mapper.insert(data);
        organization.assignIdentity(OrganizationId.of(data.getId()));
        return organization;
    }

    @Override
    public void update(Organization organization) {
        if (organization.getId() == null
                || mapper.update(toData(organization)) != 1) {
            throw new DomainException("组织不存在或无法更新：" + organization.getId());
        }
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<Organization> findByCode(String code) {
        return Optional.ofNullable(mapper.selectByCode(code)).map(this::toDomain);
    }

    @Override
    public List<Organization> findByIds(List<OrganizationId> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return mapper.selectByIds(ids.stream().map(OrganizationId::value).toList()).stream()
                .map(this::toDomain)
                .toList();
    }

    private OrganizationData toData(Organization organization) {
        OrganizationData data = new OrganizationData();
        data.setId(organization.getId() == null ? null : organization.getId().value());
        data.setCode(organization.getCode());
        data.setName(organization.getName());
        data.setDescription(organization.getDescription());
        data.setStatus(organization.getStatus().name());
        data.setCreatedBy(organization.getCreatedBy().value());
        data.setCreatedAt(toDb(organization.getCreatedAt()));
        data.setUpdatedAt(toDb(organization.getUpdatedAt()));
        return data;
    }

    private Organization toDomain(OrganizationData data) {
        return Organization.restore(
                OrganizationId.of(data.getId()),
                data.getCode(),
                data.getName(),
                data.getDescription(),
                OrganizationStatus.parse(data.getStatus()),
                UserId.of(data.getCreatedBy()),
                toInstant(data.getCreatedAt()),
                toInstant(data.getUpdatedAt()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
