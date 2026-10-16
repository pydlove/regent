package com.pydlove.regent.bug.application.command;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

/** 缺陷单写侧用例。 */
public class BugCommandService {

    private final BugRepository bugRepository;
    private final ProjectProfileRepository projectProfileRepository;

    public BugCommandService(BugRepository bugRepository, ProjectProfileRepository projectProfileRepository) {
        this.bugRepository = bugRepository;
        this.projectProfileRepository = projectProfileRepository;
    }

    public Bug createManual(CreateBugCommand command) {
        projectProfileRepository.findById(command.projectId())
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + command.projectId()));
        Bug bug = Bug.reportManually(
                command.projectId(),
                command.title(),
                command.severity(),
                command.reproduction(),
                command.filePath(),
                command.detail());
        bugRepository.save(bug);
        return bug;
    }
}
