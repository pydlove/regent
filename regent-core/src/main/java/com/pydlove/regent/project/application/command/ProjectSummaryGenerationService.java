package com.pydlove.regent.project.application.command;

import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryRequest;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.port.ProjectSummaryGenerator;
import com.pydlove.regent.project.domain.port.ProjectSummaryScheduler;
import com.pydlove.regent.project.domain.repository.ProjectRepository;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 项目资料生成编排。
 *
 * <p>注册事务提交后异步执行；进程内状态只用于阻止同一实例重复提交，持久化状态仍是事实源。
 */
public class ProjectSummaryGenerationService implements ProjectSummaryScheduler {

    private static final String RESTART_FAILURE = "平台重启，项目资料生成已中断，请重新生成";

    private final ProjectRepository projectRepository;
    private final ProjectSummaryGenerator generator;
    private final Executor workers;
    private final Set<Long> activeProjects = ConcurrentHashMap.newKeySet();

    public ProjectSummaryGenerationService(ProjectRepository projectRepository,
                                           ProjectSummaryGenerator generator,
                                           Executor workers) {
        this.projectRepository = projectRepository;
        this.generator = generator;
        this.workers = workers;
    }

    @Override
    public void schedule(ProjectId projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }
        if (!project.hasWorkspace()) {
            throw new com.pydlove.regent.shared.domain.DomainException(
                    "项目尚未绑定本机代码目录，不能生成项目资料");
        }
        if (!activeProjects.add(projectId.value())) {
            return;
        }
        try {
            project.startSummaryGeneration();
            projectRepository.update(project);
            workers.execute(() -> generate(projectId));
        } catch (RuntimeException e) {
            activeProjects.remove(projectId.value());
            fail(projectId, "项目资料生成未能启动：" + messageOf(e));
        }
    }

    /** 启动恢复：上一进程留下的生成中状态不会永远卡住，转成可重试的失败。 */
    public int recoverInterrupted() {
        int recovered = 0;
        for (Project project : projectRepository.findBySummaryStatus(ProjectSummaryStatus.GENERATING)) {
            try {
                project.failSummary(RESTART_FAILURE);
                projectRepository.update(project);
                recovered++;
            } catch (RuntimeException ignored) {
                // 单条恢复失败不阻断其它项目；下一次启动或人工排查还能继续处理。
            }
        }
        return recovered;
    }

    private void generate(ProjectId projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalStateException("项目已不存在：" + projectId));
            String summary = generator.generate(new ProjectSummaryRequest(
                    project.getId(),
                    project.getCode(),
                    project.getName(),
                    project.getPath(),
                    project.getTechStack(),
                    project.getVerifyMethod()));

            Project fresh = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalStateException("项目已不存在：" + projectId));
            fresh.completeSummary(summary);
            projectRepository.update(fresh);
        } catch (RuntimeException e) {
            fail(projectId, "项目资料生成失败：" + messageOf(e));
        } finally {
            activeProjects.remove(projectId.value());
        }
    }

    private void fail(ProjectId projectId, String reason) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project != null && project.getSummaryStatus() == ProjectSummaryStatus.GENERATING) {
                project.failSummary(reason);
                projectRepository.update(project);
            }
        } catch (RuntimeException ignored) {
            // 失败原因落库失败时保留原状态，至少启动恢复还能看见它。
        }
    }

    private static String messageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
