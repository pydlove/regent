package com.pydlove.regent.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.execution.domain.port.AgentExecutor;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.requirement.application.command.RequirementCommandService;
import com.pydlove.regent.requirement.application.command.RequirementIdeaService;
import com.pydlove.regent.requirement.application.command.RequirementReviewRecordService;
import com.pydlove.regent.requirement.application.query.RequirementQueryService;
import com.pydlove.regent.requirement.domain.port.RequirementPlanner;
import com.pydlove.regent.requirement.domain.port.RequirementIdeaDesigner;
import com.pydlove.regent.requirement.domain.port.RequirementPurgePort;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.requirement.domain.repository.RequirementReviewAttachmentRepository;
import com.pydlove.regent.requirement.domain.repository.RequirementReviewRecordRepository;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import com.pydlove.regent.requirement.domain.repository.TaskDependencyRepository;
import com.pydlove.regent.requirement.infrastructure.planning.CodexRequirementPlanner;
import com.pydlove.regent.requirement.infrastructure.planning.CodexRequirementIdeaDesigner;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.shared.domain.port.TaskStandardBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** 需求澄清与任务拆解的应用服务装配。 */
@Configuration
public class RequirementConfig {

    @Bean
    public RequirementPlanner requirementPlanner(
            AgentExecutor agentExecutor,
            ObjectMapper objectMapper,
            @Value("${regent.requirement.analysis-timeout:PT3M}") Duration timeout) {
        return new CodexRequirementPlanner(agentExecutor, objectMapper, timeout);
    }

    @Bean
    public RequirementIdeaDesigner requirementIdeaDesigner(
            AgentExecutor agentExecutor,
            ObjectMapper objectMapper,
            @Value("${regent.requirement.analysis-timeout:PT3M}") Duration timeout) {
        return new CodexRequirementIdeaDesigner(agentExecutor, objectMapper, timeout);
    }

    @Bean
    public RequirementIdeaService requirementIdeaService(
            ProjectProfileRepository projectProfileRepository,
            RequirementIdeaDesigner requirementIdeaDesigner,
            RequirementCommandService requirementCommandService,
            TransactionRunner transactionRunner) {
        return new RequirementIdeaService(
                projectProfileRepository,
                requirementIdeaDesigner,
                requirementCommandService,
                transactionRunner);
    }

    @Bean
    public RequirementCommandService requirementCommandService(
            RequirementRepository requirementRepository,
            TaskRepository taskRepository,
            TaskDependencyRepository dependencyRepository,
            ProjectProfileRepository projectProfileRepository,
            RequirementPlanner requirementPlanner,
            TransactionRunner transactionRunner,
            TaskStandardBinder taskStandardBinder,
            InitiativeRepository initiativeRepository,
            ProductVersionRepository productVersionRepository,
            RequirementDeliveryRepository requirementDeliveryRepository,
            ProjectMemberAccessService memberAccessService,
            RequirementPurgePort requirementPurgePort) {
        return new RequirementCommandService(
                requirementRepository,
                taskRepository,
                dependencyRepository,
                projectProfileRepository,
                requirementPlanner,
                transactionRunner,
                taskStandardBinder,
                initiativeRepository,
                productVersionRepository,
                requirementDeliveryRepository,
                memberAccessService,
                requirementPurgePort);
    }

    @Bean
    public RequirementQueryService requirementQueryService(
            RequirementRepository requirementRepository,
            TaskRepository taskRepository,
            TaskDependencyRepository dependencyRepository) {
        return new RequirementQueryService(
                requirementRepository, taskRepository, dependencyRepository);
    }

    @Bean
    public RequirementReviewRecordService requirementReviewRecordService(
            RequirementRepository requirementRepository,
            RequirementReviewRecordRepository recordRepository,
            RequirementReviewAttachmentRepository attachmentRepository,
            ProductVersionRepository productVersionRepository,
            ProjectProfileRepository projectProfileRepository,
            ProjectMemberAccessService memberAccessService,
            RequirementCommandService requirementCommandService,
            AttachmentStorage attachmentStorage,
            TransactionRunner transactionRunner,
            @Value("${regent.documents.max-bytes}") long maxBytes) {
        return new RequirementReviewRecordService(
                requirementRepository,
                recordRepository,
                attachmentRepository,
                productVersionRepository,
                projectProfileRepository,
                memberAccessService,
                requirementCommandService,
                attachmentStorage,
                transactionRunner,
                maxBytes);
    }
}
