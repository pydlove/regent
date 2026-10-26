package com.pydlove.regent.delivery.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.BuildArtifactId;
import com.pydlove.regent.delivery.domain.model.Release;
import com.pydlove.regent.delivery.domain.model.ReleaseId;
import com.pydlove.regent.delivery.domain.model.ReleaseStage;
import com.pydlove.regent.delivery.domain.model.ReleaseStatus;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.repository.ReleaseRepository;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisReleaseRepository implements ReleaseRepository {

    private final ReleaseMapper mapper;

    public MyBatisReleaseRepository(ReleaseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Release> findById(ReleaseId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public List<Release> findByProject(ProjectId projectId) {
        return mapper.selectByProject(projectId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public int nextAttemptNo(ProjectId projectId, String version) {
        return mapper.nextAttemptNo(projectId.value(), version);
    }

    @Override
    public void save(Release release) {
        ReleaseData data = toData(release);
        if (release.getId() == null) {
            if (mapper.insert(data) != 1) throw new DomainException("发布新增失败");
            release.assignIdentity(ReleaseId.of(data.getId()));
            for (RequirementDeliveryId deliveryId : release.getDeliveryIds()) {
                if (mapper.insertItem(release.getId().value(), deliveryId.value(),
                        DeliveryTime.toDb(Instant.now())) != 1) {
                    throw new DomainException("发布需求关联新增失败");
                }
            }
        } else if (mapper.update(data) != 1) {
            throw new DomainException("发布更新失败：" + release.getId().value());
        }
    }

    private Release toDomain(ReleaseData data) {
        List<RequirementDeliveryId> deliveryIds = mapper.selectDeliveryIds(data.getId()).stream()
                .map(RequirementDeliveryId::of)
                .toList();
        return Release.restore(
                ReleaseId.of(data.getId()),
                ProjectId.of(data.getProjectId()),
                data.getVersion(),
                data.getAttemptNo(),
                BuildArtifactId.of(data.getBuildArtifactId()),
                deliveryIds,
                ReleaseStage.valueOf(data.getStage()),
                ReleaseStatus.valueOf(data.getStatus()),
                data.getReleaseNotes(),
                data.getRollbackPlan(),
                DeliveryTime.fromDb(data.getCreatedAt()),
                DeliveryTime.fromDb(data.getUpdatedAt()));
    }

    private ReleaseData toData(Release release) {
        ReleaseData data = new ReleaseData();
        data.setId(release.getId() == null ? null : release.getId().value());
        data.setProjectId(release.getProjectId().value());
        data.setVersion(release.getVersion());
        data.setAttemptNo(release.getAttemptNo());
        data.setBuildArtifactId(release.getBuildArtifactId().value());
        data.setStage(release.getStage().name());
        data.setStatus(release.getStatus().name());
        data.setReleaseNotes(release.getReleaseNotes());
        data.setRollbackPlan(release.getRollbackPlan());
        data.setCreatedAt(DeliveryTime.toDb(release.getCreatedAt()));
        data.setUpdatedAt(DeliveryTime.toDb(release.getUpdatedAt()));
        return data;
    }
}
