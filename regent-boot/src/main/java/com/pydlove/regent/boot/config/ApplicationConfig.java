package com.pydlove.regent.boot.config;

import com.pydlove.regent.bug.application.command.BugAttachmentService;
import com.pydlove.regent.bug.application.command.BugCommandService;
import com.pydlove.regent.bug.application.query.BugQueryService;
import com.pydlove.regent.bug.domain.repository.BugAttachmentRepository;
import com.pydlove.regent.bug.domain.repository.BugQueryRepository;
import com.pydlove.regent.bug.domain.repository.BugRepository;
import com.pydlove.regent.execution.application.command.BugFixDispatchService;
import com.pydlove.regent.execution.application.command.TaskAdjustmentService;
import com.pydlove.regent.execution.application.command.TaskCommandService;
import com.pydlove.regent.execution.application.command.TaskDispatchService;
import com.pydlove.regent.execution.application.command.TaskDraftService;
import com.pydlove.regent.execution.application.query.TaskQueryService;
import com.pydlove.regent.execution.application.query.TaskRunQueryService;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskAdjustmentRepository;
import com.pydlove.regent.execution.domain.repository.TaskDraftRepository;
import com.pydlove.regent.execution.domain.repository.TaskQueryRepository;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.governance.application.command.DecisionApprovalService;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.governance.application.query.DecisionQueryService;
import com.pydlove.regent.governance.domain.repository.ApprovalTokenRepository;
import com.pydlove.regent.governance.domain.repository.DecisionQueryRepository;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.governance.domain.repository.DecisionRuleRepository;
import com.pydlove.regent.project.application.command.ProjectRegistrationService;
import com.pydlove.regent.project.application.command.ProjectUpdateService;
import com.pydlove.regent.project.application.command.EnvironmentEntryService;
import com.pydlove.regent.project.application.query.DirectoryListingService;
import com.pydlove.regent.project.application.query.ProjectAccessService;
import com.pydlove.regent.project.application.query.ProjectGitBranchQueryService;
import com.pydlove.regent.project.domain.port.ProjectSummaryScheduler;
import com.pydlove.regent.project.domain.repository.EnvironmentEntryRepository;
import com.pydlove.regent.project.domain.port.ProjectGitBranchReader;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.domain.repository.ProjectRepository;
import com.pydlove.regent.project.infrastructure.git.GitProjectGitBranchReader;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

/**
 * 应用服务的显式装配。
 *
 * <p>应用服务类上没有 @Service 注解 —— 它们住在 regent-core，而那个模块必须保持零框架依赖，
 * 否则它的单测就跑不进毫秒级反馈回路。代价是每个服务要在这里登记一次，
 * 收益是「core 里有没有 Spring」变成一条编译期事实，而不是靠自觉。
 */
@Configuration
public class ApplicationConfig {

    @Bean
    public BugCommandService bugCommandService(BugRepository bugRepository,
                                               ProjectProfileRepository projectProfileRepository) {
        return new BugCommandService(bugRepository, projectProfileRepository);
    }

    @Bean
    public BugFixDispatchService bugFixDispatchService(BugRepository bugRepository,
                                                       TaskRepository taskRepository,
                                                       TaskDispatchService taskDispatchService,
                                                       TransactionRunner transactionRunner) {
        return new BugFixDispatchService(
                bugRepository, taskRepository, taskDispatchService, transactionRunner);
    }

    @Bean
    public BugQueryService bugQueryService(BugQueryRepository bugQueryRepository) {
        return new BugQueryService(bugQueryRepository);
    }

    @Bean
    public BugAttachmentService bugAttachmentService(BugRepository bugRepository,
                                                     BugAttachmentRepository attachmentRepository,
                                                     AttachmentStorage attachmentStorage,
                                                     @Value("${regent.attachments.max-bytes}") long maxBytes) {
        return new BugAttachmentService(bugRepository, attachmentRepository, attachmentStorage, maxBytes);
    }

    @Bean
    public TaskQueryService taskQueryService(TaskRepository taskRepository,
                                             TaskQueryRepository taskQueryRepository) {
        return new TaskQueryService(taskRepository, taskQueryRepository, ZoneId.systemDefault());
    }

    /**
     * 写侧任务用例。它比读侧多一个依赖：录入任务前要先确认项目存在（task.project_id 有外键），
     * 而「项目存不存在」只有画像仓储答得上来。
     */
    @Bean
    public TaskCommandService taskCommandService(TaskRepository taskRepository,
                                                 ProjectProfileRepository projectProfileRepository) {
        return new TaskCommandService(taskRepository, projectProfileRepository);
    }

    @Bean
    public TaskAdjustmentService taskAdjustmentService(
            TaskRepository taskRepository,
            TaskAdjustmentRepository adjustmentRepository,
            TransactionRunner transactionRunner) {
        return new TaskAdjustmentService(taskRepository, adjustmentRepository, transactionRunner);
    }

    @Bean
    public TaskDraftService taskDraftService(TaskDraftRepository taskDraftRepository,
                                             ProjectProfileRepository projectProfileRepository) {
        return new TaskDraftService(taskDraftRepository, projectProfileRepository);
    }

    @Bean
    public ProjectRegistrationService projectRegistrationService(
            ProjectRepository projectRepository,
            ProjectSummaryScheduler projectSummaryScheduler,
            TransactionRunner transactionRunner) {
        return new ProjectRegistrationService(
                projectRepository, projectSummaryScheduler, transactionRunner);
    }

    @Bean
    public ProjectAccessService projectAccessService(ProjectProfileRepository projectProfileRepository) {
        return new ProjectAccessService(projectProfileRepository);
    }

    @Bean
    public ProjectUpdateService projectUpdateService(ProjectRepository projectRepository) {
        return new ProjectUpdateService(projectRepository);
    }

    @Bean
    public EnvironmentEntryService environmentEntryService(
            EnvironmentEntryRepository environmentEntryRepository,
            ProjectProfileRepository projectProfileRepository) {
        return new EnvironmentEntryService(environmentEntryRepository, projectProfileRepository);
    }

    /**
     * 路径选择器的读侧。它和注册用例一样只碰 {@code java.nio.file}，不经过任何仓储 ——
     * 所以这里没有依赖要递进去。
     */
    @Bean
    public DirectoryListingService directoryListingService() {
        return new DirectoryListingService();
    }

    @Bean
    public ProjectGitBranchReader projectGitBranchReader() {
        return new GitProjectGitBranchReader();
    }

    @Bean
    public ProjectGitBranchQueryService projectGitBranchQueryService(
            ProjectProfileRepository projectProfileRepository,
            ProjectGitBranchReader projectGitBranchReader) {
        return new ProjectGitBranchQueryService(projectProfileRepository, projectGitBranchReader);
    }

    @Bean
    public TaskRunQueryService taskRunQueryService(TaskRunRepository taskRunRepository,
                                                   RunEventRepository runEventRepository) {
        return new TaskRunQueryService(taskRunRepository, runEventRepository);
    }

    /**
     * 建单用例是核心域的入口：它在这里登记一次，就意味着「所有决策都要经过仲裁」这条规矩
     * 在启动期只有一个源头，没有第二条绕过去的路。
     */
    @Bean
    public DecisionRaiseService decisionRaiseService(DecisionRuleRepository decisionRuleRepository,
                                                     DecisionRepository decisionRepository) {
        return new DecisionRaiseService(decisionRuleRepository, decisionRepository);
    }

    @Bean
    public DecisionQueryService decisionQueryService(DecisionQueryRepository decisionQueryRepository) {
        return new DecisionQueryService(decisionQueryRepository);
    }

    /**
     * 审批用例是全项目唯一同时改两个聚合的地方，所以它显式依赖一个事务边界（{@link TransactionRunner}）——
     * 事务端口由基础设施层实现，这里只负责把它递进去。
     */
    @Bean
    public DecisionApprovalService decisionApprovalService(DecisionRepository decisionRepository,
                                                           ApprovalTokenRepository approvalTokenRepository,
                                                           TaskRepository taskRepository,
                                                           TransactionRunner transactionRunner) {
        return new DecisionApprovalService(
                decisionRepository, approvalTokenRepository, taskRepository, transactionRunner);
    }
}
