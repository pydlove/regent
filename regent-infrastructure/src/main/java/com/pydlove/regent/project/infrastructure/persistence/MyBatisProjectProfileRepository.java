package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.springframework.stereotype.Repository;

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
    public List<ProjectProfile> findAll() {
        return projectProfileMapper.selectAll().stream()
                .map(MyBatisProjectProfileRepository::toProfile)
                .toList();
    }

    private static ProjectProfile toProfile(ProjectProfileRow row) {
        return new ProjectProfile(
                ProjectId.of(row.getId()),
                row.getCode(),
                row.getName(),
                row.getPath(),
                DecisionLevel.parse(row.getDefaultDecisionLevel()),
                Boolean.TRUE.equals(row.getEnabled()));
    }
}
