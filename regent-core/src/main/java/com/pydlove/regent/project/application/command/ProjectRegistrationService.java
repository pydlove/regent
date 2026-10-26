package com.pydlove.regent.project.application.command;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.application.query.OrganizationAccessService;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.organization.domain.model.OrganizationRole;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.WorkflowTemplate;
import com.pydlove.regent.project.domain.model.WorkflowTemplateId;
import com.pydlove.regent.project.domain.model.WorkflowTemplateStatus;
import com.pydlove.regent.project.domain.port.ProjectSummaryScheduler;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.project.domain.repository.WorkflowTemplateRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.ForbiddenException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

/**
 * 项目注册用例 —— 平台里所有执行的前提：没有项目，任务就没有工作区可跑。
 *
 * <p>它只做应用层该做的事：查重、确认路径在本机真实存在、提交后触发一次项目资料生成。
 * 领域不变量（标识格式、名称/路径非空、路径必须绝对）全在 {@link Project#register} 里，这里不重复。
 */
public class ProjectRegistrationService {

    private final ProjectRepository projectRepository;
    private final ProjectSummaryScheduler summaryScheduler;
    private final TransactionRunner transactionRunner;
    private final ProjectMemberRepository projectMemberRepository;
    private final OrganizationAccessService organizationAccessService;
    private final WorkflowTemplateRepository workflowTemplateRepository;

    public ProjectRegistrationService(ProjectRepository projectRepository) {
        this(projectRepository, projectId -> {
        }, new TransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> action) {
                return action.get();
            }
        }, null, null, null);
    }

    public ProjectRegistrationService(ProjectRepository projectRepository,
                                      ProjectSummaryScheduler summaryScheduler,
                                      TransactionRunner transactionRunner) {
        this(projectRepository, summaryScheduler, transactionRunner, null, null, null);
    }

    public ProjectRegistrationService(ProjectRepository projectRepository,
                                      ProjectSummaryScheduler summaryScheduler,
                                      TransactionRunner transactionRunner,
                                      ProjectMemberRepository projectMemberRepository) {
        this(projectRepository, summaryScheduler, transactionRunner,
                projectMemberRepository, null, null);
    }

    public ProjectRegistrationService(ProjectRepository projectRepository,
                                      ProjectSummaryScheduler summaryScheduler,
                                      TransactionRunner transactionRunner,
                                      ProjectMemberRepository projectMemberRepository,
                                      OrganizationAccessService organizationAccessService) {
        this(projectRepository, summaryScheduler, transactionRunner,
                projectMemberRepository, organizationAccessService, null);
    }

    public ProjectRegistrationService(ProjectRepository projectRepository,
                                      ProjectSummaryScheduler summaryScheduler,
                                      TransactionRunner transactionRunner,
                                      ProjectMemberRepository projectMemberRepository,
                                      OrganizationAccessService organizationAccessService,
                                      WorkflowTemplateRepository workflowTemplateRepository) {
        this.projectRepository = projectRepository;
        this.summaryScheduler = summaryScheduler;
        this.transactionRunner = transactionRunner;
        this.projectMemberRepository = projectMemberRepository;
        this.organizationAccessService = organizationAccessService;
        this.workflowTemplateRepository = workflowTemplateRepository;
    }

    public Project register(RegisterProjectCommand command) {
        return register(command, null);
    }

    /** 注册项目并记录当前登录账号；接口侧必须走这一条。 */
    public Project register(RegisterProjectCommand command, UserId ownerId) {
        return register(command, ownerId, null);
    }

    public Project register(RegisterProjectCommand command,
                            UserId ownerId,
                            OrganizationId organizationId) {
        if (organizationAccessService != null && organizationId != null) {
            organizationAccessService.requireMember(organizationId, ownerId);
            List<OrganizationRole> roles = organizationAccessService.roles(organizationId, ownerId);
            if (roles.stream().noneMatch(OrganizationRole::canManageProjects)) {
                throw new ForbiddenException("只有组织管理员或项目经理可以创建项目");
            }
        }
        String code = resolveCode(command.code(), organizationId);
        Project project = Project.register(code, command.name(), command.path(),
                command.techStack(), command.verifyMethod(), command.defaultDecisionLevel(), ownerId,
                command.versionSource(), command.fallbackVersion(), organizationId);
        boolean duplicated = organizationId == null
                ? projectRepository.existsByCode(project.getCode())
                : projectRepository.existsByOrganizationAndCode(organizationId, project.getCode());
        if (duplicated) {
            throw new DomainException("项目标识已被占用：" + project.getCode());
        }
        if (project.hasWorkspace()) {
            ProjectDirectoryValidator.requireExistingDirectory(project.getPath());
        }
        projectRepository.save(project);
        ProjectId projectId = project.getId();
        bindWorkflowTemplate(projectId, command.workflowTemplateId());
        if (projectMemberRepository != null && ownerId != null) {
            projectMemberRepository.ensure(projectId, ownerId, ProjectMemberRole.OWNER);
        }
        if (project.hasWorkspace()) {
            transactionRunner.afterCompletion(
                    () -> summaryScheduler.schedule(projectId),
                    () -> {
                    });
        }
        return project;
    }

    private void bindWorkflowTemplate(ProjectId projectId,
                                      WorkflowTemplateId requested) {
        if (workflowTemplateRepository == null) return;
        WorkflowTemplateId templateId = requested;
        if (templateId != null) {
            WorkflowTemplate template = workflowTemplateRepository.findById(templateId)
                    .orElseThrow(() -> new DomainException(
                            "流程模板不存在：" + requested.value()));
            if (template.status() != WorkflowTemplateStatus.PUBLISHED) {
                throw new DomainException("流程模板已停用或被新版本替代，请重新选择");
            }
        } else {
            templateId = workflowTemplateRepository.findDefault()
                    .map(WorkflowTemplate::id)
                    .orElseThrow(() -> new DomainException("没有可用的研发流程模板"));
        }
        workflowTemplateRepository.bindProject(projectId, templateId, Instant.now());
    }

    private String resolveCode(String requested, OrganizationId organizationId) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String generated = "project_" + UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 10);
            boolean exists = organizationId == null
                    ? projectRepository.existsByCode(generated)
                    : projectRepository.existsByOrganizationAndCode(organizationId, generated);
            if (!exists) return generated;
        }
        throw new DomainException("项目标识生成失败，请重试");
    }

}
