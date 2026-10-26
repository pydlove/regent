package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMember;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class MyBatisProjectMemberRepository implements ProjectMemberRepository {

    private final ProjectMemberMapper mapper;

    public MyBatisProjectMemberRepository(ProjectMemberMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ProjectMember> findByProject(ProjectId projectId) {
        return mapper.selectByProject(projectId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ProjectMemberRole> findRoles(ProjectId projectId, UserId userId) {
        return mapper.selectRoles(projectId.value(), userId.value()).stream()
                .map(ProjectMemberRole::valueOf)
                .toList();
    }

    @Override
    public boolean exists(ProjectId projectId, UserId userId, ProjectMemberRole role) {
        return mapper.countRole(projectId.value(), userId.value(), role.name()) > 0;
    }

    @Override
    public int countDistinctUsers(ProjectId projectId) {
        return mapper.countDistinctUsers(projectId.value());
    }

    @Override
    public ProjectMember save(ProjectMember member) {
        ProjectMemberData data = new ProjectMemberData();
        data.setProjectId(member.projectId().value());
        data.setUserId(member.userId().value());
        data.setRoleCode(member.role().name());
        data.setCreatedAt(LocalDateTime.ofInstant(member.createdAt(), ZoneOffset.UTC));
        mapper.insert(data);
        return member.withId(data.getId());
    }

    @Override
    public void delete(ProjectId projectId, UserId userId, ProjectMemberRole role) {
        mapper.delete(projectId.value(), userId.value(), role.name());
    }

    @Override
    public void ensure(ProjectId projectId, UserId userId, ProjectMemberRole role) {
        mapper.ensure(projectId.value(), userId.value(), role.name(),
                LocalDateTime.now(ZoneOffset.UTC));
    }

    private ProjectMember toDomain(ProjectMemberData data) {
        return new ProjectMember(
                data.getId(),
                ProjectId.of(data.getProjectId()),
                UserId.of(data.getUserId()),
                ProjectMemberRole.valueOf(data.getRoleCode()),
                data.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
