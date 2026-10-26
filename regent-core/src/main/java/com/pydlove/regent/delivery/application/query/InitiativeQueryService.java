package com.pydlove.regent.delivery.application.query;

import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

public class InitiativeQueryService {

    private final InitiativeRepository initiativeRepository;
    private final ProjectProfileRepository projectProfileRepository;

    public InitiativeQueryService(InitiativeRepository initiativeRepository,
                                  ProjectProfileRepository projectProfileRepository) {
        this.initiativeRepository = initiativeRepository;
        this.projectProfileRepository = projectProfileRepository;
    }

    public List<InitiativeView> list(ProjectId projectId, UserId ownerId) {
        if (projectId == null) {
            return projectProfileRepository.findAll(ownerId).stream()
                    .flatMap(project -> initiativeRepository.findByProject(project.id()).stream()
                            .map(initiative -> new InitiativeView(initiative, project.name())))
                    .toList();
        }
        ProjectProfile project = requireProject(projectId, ownerId);
        return initiativeRepository.findByProject(projectId).stream()
                .map(initiative -> new InitiativeView(initiative, project.name()))
                .toList();
    }

    public InitiativeView get(InitiativeId id, UserId ownerId) {
        Initiative initiative = initiativeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("立项不存在：" + id.value()));
        ProjectProfile project = requireProject(initiative.getProjectId(), ownerId);
        return new InitiativeView(initiative, project.name());
    }

    private ProjectProfile requireProject(ProjectId projectId, UserId ownerId) {
        return projectProfileRepository.findById(projectId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId));
    }
}
