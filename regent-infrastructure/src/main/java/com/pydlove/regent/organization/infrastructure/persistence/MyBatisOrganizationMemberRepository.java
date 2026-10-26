package com.pydlove.regent.organization.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationMember;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.organization.domain.repository.OrganizationMemberRepository;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;

@Repository
public class MyBatisOrganizationMemberRepository implements OrganizationMemberRepository {

    private final OrganizationMemberMapper mapper;

    public MyBatisOrganizationMemberRepository(OrganizationMemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OrganizationMember member) {
        OrganizationMemberData data = new OrganizationMemberData();
        data.setOrganizationId(member.organizationId().value());
        data.setUserId(member.userId().value());
        data.setRoleCode(member.role().name());
        data.setJoinedAt(java.time.LocalDateTime.ofInstant(member.joinedAt(), ZoneOffset.UTC));
        mapper.insert(data);
    }

    @Override
    public List<OrganizationMember> findByOrganization(OrganizationId organizationId) {
        return mapper.selectByOrganization(organizationId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<OrganizationMember> findByUser(UserId userId) {
        return mapper.selectByUser(userId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<OrganizationRole> findRoles(OrganizationId organizationId, UserId userId) {
        if (userId == null) return List.of();
        return mapper.selectRoles(organizationId.value(), userId.value()).stream()
                .map(OrganizationRole::valueOf)
                .toList();
    }

    @Override
    public boolean exists(OrganizationId organizationId,
                          UserId userId,
                          OrganizationRole role) {
        return mapper.countRole(organizationId.value(), userId.value(), role.name()) > 0;
    }

    @Override
    public void deleteRole(OrganizationId organizationId,
                           UserId userId,
                           OrganizationRole role) {
        mapper.deleteRole(organizationId.value(), userId.value(), role.name());
    }

    @Override
    public void deleteMember(OrganizationId organizationId, UserId userId) {
        mapper.deleteMember(organizationId.value(), userId.value());
    }

    @Override
    public int countDistinctUsers(OrganizationId organizationId) {
        return mapper.countDistinctUsers(organizationId.value());
    }

    @Override
    public int countByRole(OrganizationId organizationId, OrganizationRole role) {
        return mapper.countByRole(organizationId.value(), role.name());
    }

    private OrganizationMember toDomain(OrganizationMemberData data) {
        return new OrganizationMember(
                data.getId(),
                OrganizationId.of(data.getOrganizationId()),
                UserId.of(data.getUserId()),
                OrganizationRole.valueOf(data.getRoleCode()),
                data.getJoinedAt().toInstant(ZoneOffset.UTC));
    }
}
