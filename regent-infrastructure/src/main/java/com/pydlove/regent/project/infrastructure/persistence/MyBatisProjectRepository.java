package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 项目仓储的 MyBatis 实现。 */
@Repository
public class MyBatisProjectRepository implements ProjectRepository {

    private final ProjectMapper projectMapper;

    public MyBatisProjectRepository(ProjectMapper projectMapper) {
        this.projectMapper = projectMapper;
    }

    @Override
    public boolean existsByCode(String code) {
        return projectMapper.countByCode(code) > 0;
    }

    @Override
    public boolean existsByOrganizationAndCode(OrganizationId organizationId, String code) {
        return organizationId != null
                && projectMapper.countByOrganizationAndCode(organizationId.value(), code) > 0;
    }

    @Override
    public void save(Project project) {
        if (project.getId() != null) {
            // 不是「暂不支持」的托词：项目字段当前全是不可变的，更新语义（哪些列能改、谁能改）
            // 还没有用例来定义。先让它在编译期之外也走不通，比造一个没人验证过的 UPDATE 安全。
            throw new DomainException("项目写侧目前只支持新增，更新用例尚未定义：" + project.getId());
        }
        ProjectData data = ProjectDataMapper.toData(project);
        projectMapper.insert(data);
        project.assignIdentity(ProjectId.of(data.getId()));
    }

    @Override
    public Optional<Project> findById(ProjectId id) {
        return Optional.ofNullable(projectMapper.selectById(id.value()))
                .map(ProjectDataMapper::toDomain);
    }

    @Override
    public Optional<Project> findByIdOwned(ProjectId id, UserId ownerId) {
        return Optional.ofNullable(projectMapper.selectByIdOwned(id.value(), ownerId.value()))
                .map(ProjectDataMapper::toDomain);
    }

    @Override
    public List<Project> findBySummaryStatus(ProjectSummaryStatus status) {
        return projectMapper.selectBySummaryStatus(status.name()).stream()
                .map(ProjectDataMapper::toDomain)
                .toList();
    }

    @Override
    public void update(Project project) {
        if (project.getId() == null) {
            throw new DomainException("未持久化的项目不能更新");
        }
        ProjectData data = ProjectDataMapper.toData(project);
        if (projectMapper.updateSummary(data) != 1) {
            throw new DomainException("项目不存在或已删除，无法更新项目资料：" + project.getId());
        }
    }

    @Override
    public void updateDetails(Project project) {
        if (project.getId() == null) {
            throw new DomainException("未持久化的项目不能更新");
        }
        ProjectData data = ProjectDataMapper.toData(project);
        if (projectMapper.updateDetails(data) != 1) {
            throw new DomainException("项目不存在或已删除，无法更新：" + project.getId());
        }
    }
}
