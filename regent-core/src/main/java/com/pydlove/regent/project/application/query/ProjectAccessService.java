package com.pydlove.regent.project.application.query;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

/**
 * 项目归属校验的唯一入口。
 *
 * <p>对外一律返回「项目不存在」，而不是「无权访问」：后者等于告诉调用方这个 ID 真实存在。
 * 项目是账户数据隔离的根，任务、决策、缺陷、部署与测试用例都通过它继承归属。
 */
public class ProjectAccessService {

    private final ProjectProfileRepository projectProfileRepository;

    public ProjectAccessService(ProjectProfileRepository projectProfileRepository) {
        this.projectProfileRepository = projectProfileRepository;
    }

    public ProjectProfile require(ProjectId projectId, UserId ownerId) {
        if (projectId == null) {
            throw new EntityNotFoundException("项目不存在");
        }
        return projectProfileRepository.findById(projectId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId));
    }
}
