package com.pydlove.regent.boot.config;

import com.pydlove.regent.delivery.application.command.InitiativeCommandService;
import com.pydlove.regent.delivery.application.command.ProductVersionCommandService;
import com.pydlove.regent.delivery.application.command.ReleaseCommandService;
import com.pydlove.regent.delivery.application.command.RequirementDeliveryCommandService;
import com.pydlove.regent.delivery.application.query.InitiativeQueryService;
import com.pydlove.regent.delivery.application.query.ProductVersionQueryService;
import com.pydlove.regent.delivery.application.query.ReleaseQueryService;
import com.pydlove.regent.delivery.application.query.RequirementDeliveryQueryService;
import com.pydlove.regent.delivery.domain.repository.BuildArtifactRepository;
import com.pydlove.regent.delivery.domain.repository.DeliveryGateRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.ProductVersionRepository;
import com.pydlove.regent.delivery.domain.repository.ReleaseRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.shared.domain.TransactionRunner;
import com.pydlove.regent.delivery.application.command.InitiativeAttachmentService;
import com.pydlove.regent.shared.domain.port.AttachmentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 研发交付流程的应用服务装配。 */
@Configuration
public class DeliveryConfig {

    @Bean
    public ProductVersionCommandService productVersionCommandService(
            ProductVersionRepository versionRepository,
            ProjectProfileRepository projectProfileRepository,
            ProjectMemberAccessService memberAccessService,
            InitiativeRepository initiativeRepository) {
        return new ProductVersionCommandService(
                versionRepository, projectProfileRepository, memberAccessService,
                initiativeRepository);
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
            TransactionRunner transactionRunner) {
        return new InitiativeCommandService(
                initiativeRepository, attachmentRepository, gateRepository, memberAccessService,
                projectProfileRepository, productVersionRepository, transactionRunner);
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
            ProjectProfileRepository projectProfileRepository) {
        return new InitiativeQueryService(initiativeRepository, projectProfileRepository);
    }

    @Bean
    public RequirementDeliveryCommandService requirementDeliveryCommandService(
            RequirementDeliveryRepository deliveryRepository,
            InitiativeRepository initiativeRepository,
            RequirementRepository requirementRepository,
            DeliveryGateRepository gateRepository,
            ProjectMemberAccessService memberAccessService,
            ProjectProfileRepository projectProfileRepository,
            TransactionRunner transactionRunner) {
        return new RequirementDeliveryCommandService(
                deliveryRepository, initiativeRepository, requirementRepository,
                gateRepository, memberAccessService, projectProfileRepository, transactionRunner);
    }

    @Bean
    public RequirementDeliveryQueryService requirementDeliveryQueryService(
            RequirementDeliveryRepository deliveryRepository,
            InitiativeRepository initiativeRepository,
            RequirementRepository requirementRepository,
            DeliveryGateRepository gateRepository,
            ProjectProfileRepository projectProfileRepository) {
        return new RequirementDeliveryQueryService(
                deliveryRepository, initiativeRepository, requirementRepository,
                gateRepository, projectProfileRepository);
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
