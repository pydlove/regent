package com.pydlove.regent.requirement.application.command;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.requirement.domain.model.RequirementIdeaDraft;
import com.pydlove.regent.requirement.domain.model.RequirementIdeaRequest;
import com.pydlove.regent.requirement.domain.model.Requirement;
import com.pydlove.regent.requirement.domain.model.RequirementSource;
import com.pydlove.regent.requirement.domain.port.RequirementIdeaDesigner;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.List;

/** 用 Codex 把产品想法拆成多个需求，并在同一事务中写入需求池。 */
public class RequirementIdeaService {

    private final ProjectProfileRepository projectProfileRepository;
    private final RequirementIdeaDesigner designer;
    private final RequirementCommandService requirementCommandService;
    private final TransactionRunner transactionRunner;

    public RequirementIdeaService(ProjectProfileRepository projectProfileRepository,
                                  RequirementIdeaDesigner designer,
                                  RequirementCommandService requirementCommandService,
                                  TransactionRunner transactionRunner) {
        this.projectProfileRepository = projectProfileRepository;
        this.designer = designer;
        this.requirementCommandService = requirementCommandService;
        this.transactionRunner = transactionRunner;
    }

    public List<Requirement> generate(ProjectId projectId,
                                      InitiativeId initiativeId,
                                      String idea,
                                      UserId ownerId) {
        if (idea == null || idea.isBlank()) {
            throw new DomainException("请先写下产品想法");
        }
        if (initiativeId == null) {
            throw new DomainException("请选择所属版本立项");
        }
        ProjectProfile project = projectProfileRepository.findById(projectId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "项目不存在：" + projectId.value()));
        List<RequirementIdeaDraft> drafts = designer.design(new RequirementIdeaRequest(
                project.id(),
                project.code(),
                project.name(),
                project.path(),
                project.summary(),
                idea.trim()));
        if (drafts == null || drafts.isEmpty()) {
            throw new DomainException("AI 没有生成可用需求");
        }
        return transactionRunner.inTransaction(() -> drafts.stream()
                .map(draft -> requirementCommandService.create(
                        new SaveRequirementCommand(
                                projectId,
                                initiativeId,
                                RequirementSource.AI,
                                draft.title(),
                                draft.rawText(),
                                draft.acceptanceText(),
                                clarificationText(draft)),
                        ownerId))
                .toList());
    }

    private static String clarificationText(RequirementIdeaDraft draft) {
        StringBuilder text = new StringBuilder();
        if (!draft.questions().isEmpty()) {
            text.append("待确认问题：\n");
            draft.questions().forEach(question ->
                    text.append("- ").append(question).append('\n'));
        }
        if (!draft.assumptions().isEmpty()) {
            if (!text.isEmpty()) text.append('\n');
            text.append("AI 假设：\n");
            draft.assumptions().forEach(assumption ->
                    text.append("- ").append(assumption).append('\n'));
        }
        return text.toString().trim();
    }
}
