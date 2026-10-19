package com.pydlove.regent.project.application.command;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.port.ProjectSummaryScheduler;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.TransactionRunner;

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

    public ProjectRegistrationService(ProjectRepository projectRepository) {
        this(projectRepository, projectId -> {
        }, new TransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> action) {
                return action.get();
            }
        });
    }

    public ProjectRegistrationService(ProjectRepository projectRepository,
                                      ProjectSummaryScheduler summaryScheduler,
                                      TransactionRunner transactionRunner) {
        this.projectRepository = projectRepository;
        this.summaryScheduler = summaryScheduler;
        this.transactionRunner = transactionRunner;
    }

    public Project register(RegisterProjectCommand command) {
        return register(command, null);
    }

    /** 注册项目并记录当前登录账号；接口侧必须走这一条。 */
    public Project register(RegisterProjectCommand command, UserId ownerId) {
        Project project = Project.register(command.code(), command.name(), command.path(),
                command.techStack(), command.verifyMethod(), command.defaultDecisionLevel(), ownerId);
        if (projectRepository.existsByCode(project.getCode())) {
            throw new DomainException("项目标识已被占用：" + project.getCode());
        }
        ProjectDirectoryValidator.requireExistingDirectory(project.getPath());
        projectRepository.save(project);
        ProjectId projectId = project.getId();
        transactionRunner.afterCompletion(
                () -> summaryScheduler.schedule(projectId),
                () -> {
                });
        return project;
    }

}
