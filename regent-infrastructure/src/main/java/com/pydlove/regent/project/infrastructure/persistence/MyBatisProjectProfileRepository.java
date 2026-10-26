package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.model.ProjectVersionSource;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 项目画像查询的 MyBatis 实现。 */
@Repository
public class MyBatisProjectProfileRepository implements ProjectProfileRepository {

    private final ProjectProfileMapper projectProfileMapper;

    public MyBatisProjectProfileRepository(ProjectProfileMapper projectProfileMapper) {
        this.projectProfileMapper = projectProfileMapper;
    }

    @Override
    public Optional<ProjectProfile> findById(ProjectId id) {
        return Optional.ofNullable(projectProfileMapper.selectById(id.value())).map(MyBatisProjectProfileRepository::toProfile);
    }

    @Override
    public Optional<ProjectProfile> findById(ProjectId id, UserId ownerId) {
        return Optional.ofNullable(projectProfileMapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(MyBatisProjectProfileRepository::toProfile);
    }

    @Override
    public Optional<ProjectProfile> findByCode(String code) {
        return Optional.ofNullable(projectProfileMapper.selectByCode(code))
                .map(MyBatisProjectProfileRepository::toProfile);
    }

    @Override
    public List<ProjectProfile> findAll() {
        return projectProfileMapper.selectAll().stream()
                .map(MyBatisProjectProfileRepository::toProfile)
                .toList();
    }

    @Override
    public List<ProjectProfile> findAll(UserId ownerId) {
        return projectProfileMapper.selectAllOwned(ownerId.value(), null).stream()
                .map(MyBatisProjectProfileRepository::toProfile)
                .toList();
    }

    @Override
    public List<ProjectProfile> findAll(UserId ownerId, OrganizationId organizationId) {
        return projectProfileMapper.selectAllOwned(
                        ownerId.value(), organizationId == null ? null : organizationId.value()).stream()
                .map(MyBatisProjectProfileRepository::toProfile)
                .toList();
    }

    @Override
    public List<ProjectProfile> findByOrganization(OrganizationId organizationId) {
        return projectProfileMapper.selectByOrganization(organizationId.value()).stream()
                .map(MyBatisProjectProfileRepository::toProfile)
                .toList();
    }

    @Override
    public long countByOrganization(OrganizationId organizationId) {
        return projectProfileMapper.countByOrganization(organizationId.value());
    }

    private static ProjectProfile toProfile(ProjectProfileRow row) {
        return new ProjectProfile(
                ProjectId.of(row.getId()),
                row.getOwnerId() == null ? null : UserId.of(row.getOwnerId()),
                row.getCode(),
                row.getName(),
                row.getPath(),
                DecisionLevel.parse(row.getDefaultDecisionLevel()),
                Boolean.TRUE.equals(row.getEnabled()),
                ProjectSummaryStatus.parse(row.getSummaryStatus()),
                row.getSummary(),
                row.getSummaryError(),
                row.getSummaryGeneratedAt() == null
                        ? null : row.getSummaryGeneratedAt().toInstant(ZoneOffset.UTC),
                ProjectVersionSource.parse(row.getVersionSource()),
                row.getFallbackVersion(),
                row.getOrganizationId() == null ? null : OrganizationId.of(row.getOrganizationId()));
    }
}
