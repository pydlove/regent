package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

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
}
