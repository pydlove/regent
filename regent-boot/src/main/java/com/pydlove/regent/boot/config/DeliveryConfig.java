package com.pydlove.regent.boot.config;

import com.pydlove.regent.delivery.application.command.InitiativeCommandService;
import com.pydlove.regent.delivery.application.command.ProductVersionCommandService;
import com.pydlove.regent.delivery.application.command.ReleaseCommandService;
import com.pydlove.regent.delivery.application.command.RequirementDeliveryCommandService;
import com.pydlove.regent.delivery.application.command.RequirementDesignAttachmentService;
import com.pydlove.regent.delivery.application.command.RequirementDesignCommandService;
import com.pydlove.regent.delivery.application.query.InitiativeQueryService;
import com.pydlove.regent.delivery.application.query.ProductVersionQueryService;
import com.pydlove.regent.delivery.application.query.ReleaseQueryService;
import com.pydlove.regent.delivery.application.query.RequirementDeliveryQueryService;
import com.pydlove.regent.delivery.domain.repository.BuildArtifactRepository;
import com.pydlove.regent.delivery.domain.repository.DeliveryGateRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativePlanRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.delivery.domain.repository.ReleaseRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignModuleRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.domain.repository.ProjectMemberRepository;
import com.pydlove.regent.project.domain.repository.WorkflowTemplateRepository;
import com.pydlove.regent.project.application.command.WorkflowTemplateCommandService;
import com.pydlove.regent.project.application.query.WorkflowTemplateQueryService;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.delivery.application.command.InitiativeAttachmentService;
import com.pydlove.regent.delivery.application.command.InitiativePlanCommandService;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 研发交付流程的应用服务装配。 */
@Configuration
public class DeliveryConfig {

    @Bean
    public ProductVersionCommandService productVersionCommandService(
            ProductVersionRepository versionRepository,
            ProjectProfileRepository projectProfileRepository,
            ProjectMemberAccessService memberAccessService,
            InitiativeRepository initiativeRepository,
            RequirementRepository requirementRepository,
            WorkflowTemplateRepository workflowTemplateRepository,
            TransactionRunner transactionRunner) {
        return new ProductVersionCommandService(
                versionRepository, projectProfileRepository, memberAccessService,
                initiativeRepository, requirementRepository, workflowTemplateRepository,
                transactionRunner);
    }

    @Bean
    public WorkflowTemplateQueryService workflowTemplateQueryService(
            WorkflowTemplateRepository workflowTemplateRepository) {
        return new WorkflowTemplateQueryService(workflowTemplateRepository);
    }

    @Bean
    public WorkflowTemplateCommandService workflowTemplateCommandService(
            WorkflowTemplateRepository workflowTemplateRepository,
            TransactionRunner transactionRunner) {
        return new WorkflowTemplateCommandService(
                workflowTemplateRepository, transactionRunner, Clock.systemUTC());
    }

    @Bean
    public ProductVersionQueryService productVersionQueryService(
            ProductVersionRepository versionRepository,
            ProjectProfileRepository projectProfileRepository) {
        return new ProductVersionQueryService(versionRepository, projectProfileRepository);
    }

    @Bean
    public InitiativeCommandService initiativeCommandService(
            InitiativeRepository initiativeRepository,
            InitiativeAttachmentRepository attachmentRepository,
            DeliveryGateRepository gateRepository,
            ProjectMemberAccessService memberAccessService,
            ProjectProfileRepository projectProfileRepository,
            ProductVersionRepository productVersionRepository,
            InitiativePlanRepository initiativePlanRepository,
            TransactionRunner transactionRunner) {
        return new InitiativeCommandService(
                initiativeRepository, attachmentRepository, gateRepository, memberAccessService,
                projectProfileRepository, productVersionRepository,
                initiativePlanRepository, transactionRunner);
    }

    @Bean
    public InitiativePlanCommandService initiativePlanCommandService(
            InitiativeRepository initiativeRepository,
            InitiativePlanRepository planRepository,
            ProjectProfileRepository projectProfileRepository,
            ProjectMemberAccessService memberAccessService,
            TransactionRunner transactionRunner) {
        return new InitiativePlanCommandService(
                initiativeRepository, planRepository, projectProfileRepository,
                memberAccessService, transactionRunner);
    }

    @Bean
    public InitiativeAttachmentService initiativeAttachmentService(
            InitiativeRepository initiativeRepository,
            InitiativeAttachmentRepository attachmentRepository,
            ProjectProfileRepository projectProfileRepository,
            AttachmentStorage attachmentStorage,
            @Value("${regent.documents.max-bytes}") long maxBytes) {
        return new InitiativeAttachmentService(
                initiativeRepository, attachmentRepository, projectProfileRepository,
                attachmentStorage, maxBytes);
    }

    @Bean
    public InitiativeQueryService initiativeQueryService(
            InitiativeRepository initiativeRepository,
            InitiativePlanRepository planRepository,
            ProjectProfileRepository projectProfileRepository) {
        return new InitiativeQueryService(
                initiativeRepository, planRepository, projectProfileRepository);
    }

    @Bean
    public RequirementDeliveryCommandService requirementDeliveryCommandService(
            RequirementDeliveryRepository deliveryRepository,
            InitiativeRepository initiativeRepository,
            RequirementRepository requirementRepository,
            DeliveryGateRepository gateRepository,
            ProjectMemberAccessService memberAccessService,
            ProjectProfileRepository projectProfileRepository,
            RequirementDesignModuleRepository moduleRepository,
            RequirementDesignAttachmentRepository designAttachmentRepository,
            TransactionRunner transactionRunner) {
        return new RequirementDeliveryCommandService(
                deliveryRepository, initiativeRepository, requirementRepository,
                gateRepository, memberAccessService, projectProfileRepository, transactionRunner,
                moduleRepository, designAttachmentRepository);
    }

    @Bean
    public RequirementDeliveryQueryService requirementDeliveryQueryService(
            RequirementDeliveryRepository deliveryRepository,
            InitiativeRepository initiativeRepository,
            RequirementRepository requirementRepository,
            DeliveryGateRepository gateRepository,
            ProjectProfileRepository projectProfileRepository,
            RequirementDesignModuleRepository moduleRepository,
            RequirementDesignAttachmentRepository designAttachmentRepository) {
        return new RequirementDeliveryQueryService(
                deliveryRepository, initiativeRepository, requirementRepository,
                gateRepository, projectProfileRepository,
                moduleRepository, designAttachmentRepository);
    }

    @Bean
    public RequirementDesignCommandService requirementDesignCommandService(
            RequirementDeliveryRepository deliveryRepository,
            RequirementDesignModuleRepository moduleRepository,
            RequirementDesignAttachmentRepository attachmentRepository,
            ProjectMemberRepository memberRepository,
            ProjectMemberAccessService memberAccessService,
            ProjectProfileRepository projectProfileRepository,
            TransactionRunner transactionRunner) {
        return new RequirementDesignCommandService(
                deliveryRepository, moduleRepository, attachmentRepository,
                memberRepository, memberAccessService, projectProfileRepository,
                transactionRunner);
    }

    @Bean
    public RequirementDesignAttachmentService requirementDesignAttachmentService(
            RequirementDeliveryRepository deliveryRepository,
            RequirementDesignModuleRepository moduleRepository,
            RequirementDesignAttachmentRepository attachmentRepository,
            ProjectProfileRepository projectProfileRepository,
            ProjectMemberAccessService memberAccessService,
            AttachmentStorage attachmentStorage,
            @Value("${regent.documents.max-bytes}") long maxBytes) {
        return new RequirementDesignAttachmentService(
                deliveryRepository, moduleRepository, attachmentRepository,
                projectProfileRepository, memberAccessService,
                attachmentStorage, maxBytes);
    }

    @Bean
    public ReleaseCommandService releaseCommandService(
            ReleaseRepository releaseRepository,
            BuildArtifactRepository artifactRepository,
            RequirementDeliveryRepository deliveryRepository,
            DeliveryGateRepository gateRepository,
            ProjectMemberAccessService memberAccessService,
            ProjectProfileRepository projectProfileRepository,
            TransactionRunner transactionRunner) {
        return new ReleaseCommandService(
                releaseRepository, artifactRepository, deliveryRepository,
                gateRepository, memberAccessService, projectProfileRepository, transactionRunner);
    }

    @Bean
    public ReleaseQueryService releaseQueryService(
            ReleaseRepository releaseRepository,
            BuildArtifactRepository artifactRepository,
            RequirementDeliveryRepository deliveryRepository,
            DeliveryGateRepository gateRepository,
            ProjectProfileRepository projectProfileRepository,
            RequirementDeliveryQueryService deliveryQueryService) {
        return new ReleaseQueryService(
                releaseRepository, artifactRepository, deliveryRepository,
                gateRepository, projectProfileRepository, deliveryQueryService);
    }
}
