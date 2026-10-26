package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.WorkflowTemplate;
import com.pydlove.regent.project.domain.model.WorkflowTemplateDraft;
import com.pydlove.regent.project.domain.model.WorkflowTemplateId;
import com.pydlove.regent.project.domain.model.WorkflowTemplateStage;
import com.pydlove.regent.project.domain.model.WorkflowTemplateStatus;
import com.pydlove.regent.project.domain.repository.WorkflowTemplateRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 全局流程模板的写侧。
 *
 * <p>已发布版本从不原地修改。编辑既有流程时复制成同编码的下一个版本，
 * 并把上一发布版本标记为已替代；这样绑定到旧版本的项目和产品版本不会被动漂移。
 */
public class WorkflowTemplateCommandService {

    private final WorkflowTemplateRepository repository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public WorkflowTemplateCommandService(
            WorkflowTemplateRepository repository,
            TransactionRunner transactionRunner,
            Clock clock) {
        this.repository = repository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public WorkflowTemplate create(SaveWorkflowTemplateCommand command) {
        String code = "workflow_" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12);
        WorkflowTemplateDraft draft = draft(code, 1, command);
        return transactionRunner.inTransaction(
                () -> repository.create(draft, clock.instant()));
    }

    public WorkflowTemplate createVersion(
            WorkflowTemplateId sourceId,
            SaveWorkflowTemplateCommand command) {
        WorkflowTemplate source = repository.findById(sourceId)
                .orElseThrow(() -> new EntityNotFoundException("流程模板不存在：" + sourceId.value()));
        int versionNo = repository.nextVersionNo(source.code());
        WorkflowTemplateDraft draft = draft(source.code(), versionNo, command);
        return transactionRunner.inTransaction(() -> {
            repository.supersedePublished(source.code(), clock.instant());
            return repository.create(draft, clock.instant());
        });
    }

    public WorkflowTemplate archive(WorkflowTemplateId templateId) {
        WorkflowTemplate template = repository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("流程模板不存在：" + templateId.value()));
        if (template.status() == WorkflowTemplateStatus.ARCHIVED) {
            return template;
        }
        if (repository.countBindings(templateId) > 0) {
            throw new DomainException("该模板已被项目或产品版本使用，不能停用；请复制新版本替代");
        }
        if (template.selectable() && repository.findPublished().size() <= 1) {
            throw new DomainException("至少需要保留一个可用于新建项目的流程模板");
        }
        WorkflowTemplate archived = template.archived();
        return transactionRunner.inTransaction(() -> {
            repository.updateStatus(archived, clock.instant());
            return archived;
        });
    }

    private WorkflowTemplateDraft draft(
            String code,
            int versionNo,
            SaveWorkflowTemplateCommand command) {
        if (command == null) {
            throw new DomainException("缺少流程模板内容");
        }
        List<WorkflowTemplateStage> stages = new ArrayList<>();
        for (int index = 0; index < command.stages().size(); index++) {
            SaveWorkflowTemplateCommand.Stage stage = command.stages().get(index);
            if (stage == null) {
                throw new DomainException("流程阶段不能为空");
            }
            String stageCode = stage.code();
            if (stageCode == null || stageCode.isBlank()) {
                stageCode = "STAGE_" + UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase();
            }
            stages.add(new WorkflowTemplateStage(
                    stageCode,
                    stage.name(),
                    index + 1,
                    stage.scope(),
                    stage.approverRole(),
                    stage.description()));
        }
        return new WorkflowTemplateDraft(
                code, command.name(), versionNo, command.description(), stages);
    }
}
