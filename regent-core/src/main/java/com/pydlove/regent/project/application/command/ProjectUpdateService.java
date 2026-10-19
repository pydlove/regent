package com.pydlove.regent.project.application.command;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

/**
 * 项目画像编辑用例。只修改可编辑字段，并确保新路径仍是本机真实目录。
 */
public class ProjectUpdateService {

    private final ProjectRepository projectRepository;

    public ProjectUpdateService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project update(ProjectId id, UpdateProjectCommand command) {
        return update(id, command, null);
    }

    /** 只允许项目归属账号修改自己的项目。 */
    public Project update(ProjectId id, UpdateProjectCommand command, UserId ownerId) {
        Project project = (ownerId == null
                ? projectRepository.findById(id)
                : projectRepository.findByIdOwned(id, ownerId))
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + id.value()));
        ProjectDirectoryValidator.requireExistingDirectory(command.path());
        project.updateDetails(command.name(), command.path(),
                command.defaultDecisionLevel(), command.enabled());
        projectRepository.updateDetails(project);
        return project;
    }
}
