package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.delivery.domain.model.BuildArtifact;
import com.pydlove.regent.delivery.domain.model.DeliveryGate;
import com.pydlove.regent.delivery.domain.model.GateDecision;
import com.pydlove.regent.delivery.domain.model.GateScope;
import com.pydlove.regent.delivery.domain.model.GateType;
import com.pydlove.regent.delivery.domain.model.Release;
import com.pydlove.regent.delivery.domain.model.ReleaseId;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.repository.BuildArtifactRepository;
import com.pydlove.regent.delivery.domain.repository.DeliveryGateRepository;
import com.pydlove.regent.delivery.domain.repository.ReleaseRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.application.query.ProjectMemberAccessService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.util.List;

/** 发布轮次的创建、转测、集成、回归和发版推进。 */
public class ReleaseCommandService {

    private final ReleaseRepository releaseRepository;
    private final BuildArtifactRepository artifactRepository;
    private final RequirementDeliveryRepository deliveryRepository;
    private final DeliveryGateRepository gateRepository;
    private final ProjectMemberAccessService memberAccessService;
    private final ProjectProfileRepository projectProfileRepository;
    private final TransactionRunner transactionRunner;

    public ReleaseCommandService(ReleaseRepository releaseRepository,
                                 BuildArtifactRepository artifactRepository,
                                 RequirementDeliveryRepository deliveryRepository,
                                 DeliveryGateRepository gateRepository,
                                 ProjectMemberAccessService memberAccessService,
                                 ProjectProfileRepository projectProfileRepository,
                                 TransactionRunner transactionRunner) {
        this.releaseRepository = releaseRepository;
        this.artifactRepository = artifactRepository;
        this.deliveryRepository = deliveryRepository;
        this.gateRepository = gateRepository;
        this.memberAccessService = memberAccessService;
        this.projectProfileRepository = projectProfileRepository;
        this.transactionRunner = transactionRunner;
    }

    public Release create(ProjectId projectId,
                          String version,
                          String gitCommit,
                          String digest,
                          long sizeBytes,
                          String storageRef,
                          String releaseNotes,
                          String rollbackPlan,
                          List<RequirementDeliveryId> deliveryIds,
                          UserId ownerId) {
        return transactionRunner.inTransaction(() -> {
            requireProject(projectId, ownerId);
            memberAccessService.requireRole(projectId, ownerId, ProjectMemberRole.OWNER);
            List<RequirementDelivery> deliveries = deliveryRepository.findByIds(deliveryIds);
            if (deliveries.size() != deliveryIds.size()) {
                throw new DomainException("部分需求交付不存在");
            }
            for (RequirementDelivery delivery : deliveries) {
                if (!delivery.getProjectId().equals(projectId)) {
                    throw new DomainException("需求交付与发布不属于同一个项目");
                }
                if (!delivery.readyForRelease()) {
                    throw new DomainException("需求交付尚未达到待转测阶段：" + delivery.getId().value());
                }
            }
            BuildArtifact artifact = BuildArtifact.create(
                    projectId, version, gitCommit, digest, sizeBytes, storageRef, ownerId);
            artifactRepository.save(artifact);
            int attemptNo = releaseRepository.nextAttemptNo(projectId, version);
            Release release = Release.create(projectId, version, attemptNo, artifact.getId(),
                    deliveryIds, releaseNotes, rollbackPlan);
            releaseRepository.save(release);
            for (RequirementDelivery delivery : deliveries) {
                delivery.markInRelease();
                deliveryRepository.save(delivery);
            }
            return release;
        });
    }

    public Release act(ReleaseId id, ReleaseAction action, String comment, UserId ownerId) {
        return transactionRunner.inTransaction(() -> {
            Release release = load(id, ownerId);
            ProjectMemberRole requiredRole = requiredRoleFor(action);
            var grant = memberAccessService.requireRole(
                    release.getProjectId(), ownerId, requiredRole);
            GateType gateType;
            GateDecision decision = GateDecision.PASSED;
            switch (action) {
                case APPROVE_TRANSFER -> {
                    release.approveTransfer();
                    gateType = GateType.TRANSFER_APPROVED;
                }
                case PASS_INTEGRATION -> {
                    release.passIntegration();
                    gateType = GateType.INTEGRATION_PASSED;
                }
                case PASS_REGRESSION -> {
                    release.passRegression();
                    gateType = GateType.REGRESSION_PASSED;
                }
                case APPROVE_RELEASE -> {
                    release.approveRelease();
                    gateType = GateType.RELEASE_APPROVED;
                }
                case MARK_DEPLOYED -> {
                    release.markDeployed();
                    gateType = GateType.PRODUCTION_DEPLOYED;
                }
                case CLOSE -> {
                    release.close();
                    gateType = GateType.RELEASE_CLOSED;
                }
                default -> throw new DomainException("不支持的发布动作：" + action);
            }
            releaseRepository.save(release);
            gateRepository.save(DeliveryGate.record(
                    GateScope.RELEASE,
                    release.getId().value(),
                    gateType,
                    decision,
                    grant.role().name(),
                    withFallback(comment, grant.soloFallback()),
                    ownerId));
            if (action == ReleaseAction.MARK_DEPLOYED || action == ReleaseAction.CLOSE) {
                for (RequirementDeliveryId deliveryId : release.getDeliveryIds()) {
                    RequirementDelivery delivery = deliveryRepository.findById(deliveryId)
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "需求交付不存在：" + deliveryId.value()));
                    if (action == ReleaseAction.MARK_DEPLOYED) delivery.markReleased();
                    if (action == ReleaseAction.CLOSE) delivery.close();
                    deliveryRepository.save(delivery);
                }
            }
            return release;
        });
    }

    private Release load(ReleaseId id, UserId ownerId) {
        Release release = releaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("发布不存在：" + id.value()));
        requireProject(release.getProjectId(), ownerId);
        return release;
    }

    private static ProjectMemberRole requiredRoleFor(ReleaseAction action) {
        return switch (action) {
            case APPROVE_TRANSFER, PASS_INTEGRATION, PASS_REGRESSION -> ProjectMemberRole.QA;
            case APPROVE_RELEASE, MARK_DEPLOYED -> ProjectMemberRole.OWNER;
            case CLOSE -> ProjectMemberRole.OWNER;
        };
    }

    private void requireProject(ProjectId projectId, UserId ownerId) {
        projectProfileRepository.findById(projectId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId));
    }

    private static String withFallback(String comment, boolean soloFallback) {
        if (!soloFallback) return comment;
        return (comment == null || comment.isBlank() ? "" : comment.trim() + "；")
                + "单人模式代审";
    }
}
