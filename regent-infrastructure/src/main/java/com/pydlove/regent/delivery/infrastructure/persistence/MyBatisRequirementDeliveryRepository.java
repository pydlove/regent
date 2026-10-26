package com.pydlove.regent.delivery.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.DeliveryStage;
import com.pydlove.regent.delivery.domain.model.DeliveryStatus;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.model.RequirementDelivery;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.repository.RequirementDeliveryRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.requirement.domain.model.RequirementId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisRequirementDeliveryRepository implements RequirementDeliveryRepository {

    private final RequirementDeliveryMapper mapper;

    public MyBatisRequirementDeliveryRepository(RequirementDeliveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RequirementDelivery> findById(RequirementDeliveryId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public List<RequirementDelivery> findByProject(ProjectId projectId) {
        return mapper.selectByProject(projectId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public List<RequirementDelivery> findByIds(Collection<RequirementDeliveryId> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return mapper.selectByIds(ids.stream().map(RequirementDeliveryId::value).toList())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<RequirementDelivery> findActiveByRequirement(RequirementId requirementId) {
        return Optional.ofNullable(mapper.selectActiveByRequirement(requirementId.value()))
                .map(this::toDomain);
    }

    @Override
    public boolean existsStartedDesignForVersion(ProductVersionId versionId) {
        return mapper.existsStartedDesignForVersion(versionId.value());
    }

    @Override
    public void save(RequirementDelivery delivery) {
        RequirementDeliveryData data = toData(delivery);
        if (delivery.getId() == null) {
            if (mapper.insert(data) != 1) throw new DomainException("需求交付新增失败");
            delivery.assignIdentity(RequirementDeliveryId.of(data.getId()));
        } else if (mapper.update(data) != 1) {
            throw new DomainException("需求交付更新失败：" + delivery.getId().value());
        }
    }

    private RequirementDelivery toDomain(RequirementDeliveryData data) {
        return RequirementDelivery.restore(
                RequirementDeliveryId.of(data.getId()),
                InitiativeId.of(data.getInitiativeId()),
                ProjectId.of(data.getProjectId()),
                RequirementId.of(data.getRequirementId()),
                UserId.of(data.getCreatedBy()),
                DeliveryStage.valueOf(data.getStage()),
                DeliveryStatus.valueOf(data.getStatus()),
                data.getDesignSummary(),
                data.getDesignContent(),
                data.getOutlineDesignSummary(),
                data.getOutlineDesignContent(),
                data.getOutlineDesignSubmittedAt() == null
                        ? null
                        : DeliveryTime.fromDb(data.getOutlineDesignSubmittedAt()),
                data.getOutlineDesignSubmittedBy() == null
                        ? null
                        : UserId.of(data.getOutlineDesignSubmittedBy()),
                DeliveryTime.fromDb(data.getCreatedAt()),
                DeliveryTime.fromDb(data.getUpdatedAt()));
    }

    private RequirementDeliveryData toData(RequirementDelivery delivery) {
        RequirementDeliveryData data = new RequirementDeliveryData();
        data.setId(delivery.getId() == null ? null : delivery.getId().value());
        data.setInitiativeId(delivery.getInitiativeId().value());
        data.setProjectId(delivery.getProjectId().value());
        data.setRequirementId(delivery.getRequirementId().value());
        data.setCreatedBy(delivery.getCreatedBy().value());
        data.setStage(delivery.getStage().name());
        data.setStatus(delivery.getStatus().name());
        data.setDesignSummary(delivery.getDesignSummary());
        data.setDesignContent(delivery.getDesignContent());
        data.setOutlineDesignSummary(delivery.getOutlineDesignSummary());
        data.setOutlineDesignContent(delivery.getOutlineDesignContent());
        data.setOutlineDesignSubmittedAt(
                delivery.getOutlineDesignSubmittedAt() == null
                        ? null
                        : DeliveryTime.toDb(delivery.getOutlineDesignSubmittedAt()));
        data.setOutlineDesignSubmittedBy(
                delivery.getOutlineDesignSubmittedBy() == null
                        ? null
                        : delivery.getOutlineDesignSubmittedBy().value());
        data.setCreatedAt(DeliveryTime.toDb(delivery.getCreatedAt()));
        data.setUpdatedAt(DeliveryTime.toDb(delivery.getUpdatedAt()));
        return data;
    }
}
