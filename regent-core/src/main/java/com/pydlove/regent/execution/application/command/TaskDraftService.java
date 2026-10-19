package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.TaskDraft;
import com.pydlove.regent.execution.domain.repository.TaskDraftRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.Optional;

/** 当前登录用户的任务草稿读写用例。 */
public class TaskDraftService {

    private final TaskDraftRepository draftRepository;
    private final ProjectProfileRepository projectProfileRepository;

    public TaskDraftService(TaskDraftRepository draftRepository,
                            ProjectProfileRepository projectProfileRepository) {
        this.draftRepository = draftRepository;
        this.projectProfileRepository = projectProfileRepository;
    }

    public Optional<TaskDraft> current(UserId userId) {
        return draftRepository.findByUserId(userId);
    }

    public TaskDraft save(UserId userId, SaveTaskDraftCommand command) {
        if (command.projectId() != null) {
            projectProfileRepository.findById(command.projectId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "项目不存在：" + command.projectId()));
        }
        TaskDraft draft = draftRepository.findByUserId(userId)
                .orElseGet(() -> TaskDraft.create(userId));
        draft.update(command.projectId(), command.prompt(), command.priority(), command.pluginIds());
        draftRepository.save(draft);
        return draft;
    }

    public void delete(UserId userId) {
        draftRepository.deleteByUserId(userId);
    }
}
