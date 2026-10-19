package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.port.ProjectSummaryScheduler;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

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
        Project project = Project.register(command.code(), command.name(), command.path(),
                command.techStack(), command.verifyMethod(), command.defaultDecisionLevel());
        if (projectRepository.existsByCode(project.getCode())) {
            throw new DomainException("项目标识已被占用：" + project.getCode());
        }
        requireExistingDirectory(project.getPath());
        projectRepository.save(project);
        ProjectId projectId = project.getId();
        transactionRunner.afterCompletion(
                () -> summaryScheduler.schedule(projectId),
                () -> {
                });
        return project;
    }

    /**
     * 路径必须在注册时就是本机上一个真实目录。
     *
     * <p>写错一个字符，如果不在这一刻挡住，要等到第一次派发才炸 —— 那时人已经离开界面，
     * 报错躺在执行记录里，属于最贵的一种失败。
     *
     * <p>直接调 {@code java.nio.file.Files} 而不是包一层端口：这是对用户输入的一次校验，
     * 不是要适配的外部系统（对比 codex、Git 这些真正的外部依赖）。为一次 isDirectory
     * 造端口，只会让「谁在碰文件系统」变得模糊。
     */
    private static void requireExistingDirectory(String path) {
        try {
            if (!Files.isDirectory(Path.of(path))) {
                throw new DomainException("项目路径不存在或不是目录：" + path);
            }
        } catch (InvalidPathException e) {
            throw new DomainException("项目路径不是合法路径：" + path);
        }
    }
}
