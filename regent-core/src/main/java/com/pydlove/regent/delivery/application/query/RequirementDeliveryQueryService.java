package com.pydlove.regent.delivery.application.query;

import com.pydlove.regent.delivery.domain.model.GateScope;
import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.repository.DeliveryGateRepository;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignAttachmentRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignModuleRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.requirement.domain.repository.RequirementRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

public class RequirementDeliveryQueryService {

    private final RequirementDeliveryRepository deliveryRepository;
    private final InitiativeRepository initiativeRepository;
    private final RequirementRepository requirementRepository;
    private final DeliveryGateRepository gateRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final RequirementDesignModuleRepository moduleRepository;
    private final RequirementDesignAttachmentRepository attachmentRepository;

    public RequirementDeliveryQueryService(RequirementDeliveryRepository deliveryRepository,
                                          InitiativeRepository initiativeRepository,
                                          RequirementRepository requirementRepository,
                                          DeliveryGateRepository gateRepository,
                                          ProjectProfileRepository projectProfileRepository) {
        this(deliveryRepository, initiativeRepository, requirementRepository, gateRepository,
                projectProfileRepository, null, null);
    }

    public RequirementDeliveryQueryService(
            RequirementDeliveryRepository deliveryRepository,
            InitiativeRepository initiativeRepository,
            RequirementRepository requirementRepository,
            DeliveryGateRepository gateRepository,
            ProjectProfileRepository projectProfileRepository,
            RequirementDesignModuleRepository moduleRepository,
            RequirementDesignAttachmentRepository attachmentRepository) {
        this.deliveryRepository = deliveryRepository;
        this.initiativeRepository = initiativeRepository;
        this.requirementRepository = requirementRepository;
        this.gateRepository = gateRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.moduleRepository = moduleRepository;
        this.attachmentRepository = attachmentRepository;
    }

    public List<RequirementDeliveryView> list(ProjectId projectId, UserId ownerId) {
        if (projectId == null) {
            return projectProfileRepository.findAll(ownerId).stream()
                    .flatMap(project -> deliveryRepository.findByProject(project.id()).stream()
                            .map(delivery -> view(delivery, project)))
                    .toList();
        }
        ProjectProfile project = requireProject(projectId, ownerId);
        return deliveryRepository.findByProject(projectId).stream()
                .map(delivery -> view(delivery, project))
                .toList();
    }

    public RequirementDeliveryView get(RequirementDeliveryId id, UserId ownerId) {
        RequirementDelivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("需求交付不存在：" + id.value()));
        return view(delivery, requireProject(delivery.getProjectId(), ownerId));
    }

    /** 发布查询已经完成归属校验，这里只需要组装交付视图。 */
    public RequirementDeliveryView getForView(RequirementDeliveryId id) {
        RequirementDelivery delivery = deliveryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("需求交付不存在：" + id.value()));
        ProjectProfile project = projectProfileRepository.findById(delivery.getProjectId())
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + delivery.getProjectId()));
        return view(delivery, project);
    }

    private RequirementDeliveryView view(RequirementDelivery delivery, ProjectProfile project) {
        String requirementTitle = requirementRepository.findById(delivery.getRequirementId())
                .map(requirement -> requirement.getTitle())
                .orElse("需求已不存在");
        String initiativeTitle = initiativeRepository.findById(delivery.getInitiativeId())
                .map(Initiative::getTitle)
                .orElse("立项已不存在");
        return new RequirementDeliveryView(
                delivery,
                requirementTitle,
                initiativeTitle,
                project.name(),
                gateRepository.findByScope(
                        GateScope.REQUIREMENT_DELIVERY, delivery.getId().value()),
                moduleRepository == null
                        ? List.of()
                        : moduleRepository.findByDelivery(delivery.getId()),
                attachmentRepository == null
                        ? List.of()
                        : attachmentRepository.findByDelivery(delivery.getId()));
    }

    private ProjectProfile requireProject(ProjectId projectId, UserId ownerId) {
        return projectProfileRepository.findById(projectId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId));
    }
}
