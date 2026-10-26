package com.pydlove.regent.delivery.application.query;

import com.pydlove.regent.delivery.domain.model.BuildArtifact;
import com.pydlove.regent.delivery.domain.model.GateScope;
import com.pydlove.regent.delivery.domain.model.Release;
import com.pydlove.regent.delivery.domain.model.ReleaseId;
import com.pydlove.regent.delivery.domain.repository.BuildArtifactRepository;
import com.pydlove.regent.delivery.domain.repository.DeliveryGateRepository;
import com.pydlove.regent.delivery.domain.repository.ReleaseRepository;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

public class ReleaseQueryService {

    private final ReleaseRepository releaseRepository;
    private final BuildArtifactRepository artifactRepository;
    private final RequirementDeliveryRepository deliveryRepository;
    private final DeliveryGateRepository gateRepository;
    private final ProjectProfileRepository projectProfileRepository;
    private final RequirementDeliveryQueryService deliveryQueryService;

    public ReleaseQueryService(ReleaseRepository releaseRepository,
                               BuildArtifactRepository artifactRepository,
                               RequirementDeliveryRepository deliveryRepository,
                               DeliveryGateRepository gateRepository,
                               ProjectProfileRepository projectProfileRepository,
                               RequirementDeliveryQueryService deliveryQueryService) {
        this.releaseRepository = releaseRepository;
        this.artifactRepository = artifactRepository;
        this.deliveryRepository = deliveryRepository;
        this.gateRepository = gateRepository;
        this.projectProfileRepository = projectProfileRepository;
        this.deliveryQueryService = deliveryQueryService;
    }

    public List<ReleaseView> list(ProjectId projectId, UserId ownerId) {
        if (projectId == null) {
            return projectProfileRepository.findAll(ownerId).stream()
                    .flatMap(project -> releaseRepository.findByProject(project.id()).stream()
                            .map(release -> view(release, project, ownerId)))
                    .toList();
        }
        ProjectProfile project = requireProject(projectId, ownerId);
        return releaseRepository.findByProject(projectId).stream()
                .map(release -> view(release, project, ownerId))
                .toList();
    }

    public ReleaseView get(ReleaseId id, UserId ownerId) {
        Release release = releaseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("发布不存在：" + id.value()));
        return view(release, requireProject(release.getProjectId(), ownerId), ownerId);
    }

    private ReleaseView view(Release release, ProjectProfile project, UserId ownerId) {
        BuildArtifact artifact = artifactRepository.findById(release.getBuildArtifactId())
                .orElseThrow(() -> new EntityNotFoundException("构建制品不存在"));
        List<RequirementDeliveryView> deliveries = release.getDeliveryIds().stream()
                .map(deliveryQueryService::getForView)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new ReleaseView(
                release,
                artifact,
                project.name(),
                deliveries,
                gateRepository.findByScope(GateScope.RELEASE, release.getId().value()));
    }

    private ProjectProfile requireProject(ProjectId projectId, UserId ownerId) {
        return projectProfileRepository.findById(projectId, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + projectId));
    }
}
