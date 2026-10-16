package com.pydlove.regent.project.application.query;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.port.ProjectGitBranchReader;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.Optional;

/**
 * 项目当前 Git 分支查询。
 */
public class ProjectGitBranchQueryService {

    private final ProjectProfileRepository projectProfileRepository;
    private final ProjectGitBranchReader projectGitBranchReader;

    public ProjectGitBranchQueryService(ProjectProfileRepository projectProfileRepository,
                                        ProjectGitBranchReader projectGitBranchReader) {
        this.projectProfileRepository = projectProfileRepository;
        this.projectGitBranchReader = projectGitBranchReader;
    }

    public Optional<String> currentBranch(ProjectId projectId) {
        String projectPath = projectProfileRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId.value()))
                .path();
        return projectGitBranchReader.currentBranch(projectPath);
    }
}
