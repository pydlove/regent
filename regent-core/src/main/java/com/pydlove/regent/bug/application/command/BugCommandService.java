package com.pydlove.regent.bug.application.command;

import com.pydlove.regent.bug.domain.model.Bug;
import com.pydlove.regent.bug.domain.model.BugId;
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

    /** 修复任务完成或人工确认代码已改完，缺陷进入待验证。 */
    public Bug markFixed(BugId id) {
        Bug bug = load(id);
        bug.markFixed();
        bugRepository.save(bug);
        return bug;
    }

    /** 人工验证通过后关闭缺陷。 */
    public Bug verify(BugId id) {
        Bug bug = load(id);
        bug.verify();
        bugRepository.save(bug);
        return bug;
    }

    /** 带理由判定不修复。理由由领域层统一校验，不能只靠前端。 */
    public Bug wontFix(BugId id, String reason) {
        Bug bug = load(id);
        bug.wontFix(reason);
        bugRepository.save(bug);
        return bug;
    }

    private Bug load(BugId id) {
        return bugRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("缺陷单不存在：" + id));
    }
}
