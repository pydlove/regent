package com.pydlove.regent.delivery.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.Initiative;
import com.pydlove.regent.delivery.domain.model.InitiativeId;
import com.pydlove.regent.delivery.domain.model.InitiativeStatus;
import com.pydlove.regent.delivery.domain.model.ProductVersionId;
import com.pydlove.regent.delivery.domain.repository.InitiativeRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisInitiativeRepository implements InitiativeRepository {

    private final InitiativeMapper mapper;

    public MyBatisInitiativeRepository(InitiativeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Initiative> findById(InitiativeId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public List<Initiative> findByProject(ProjectId projectId) {
        return mapper.selectByProject(projectId.value()).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Initiative> findByVersion(ProductVersionId versionId) {
        return Optional.ofNullable(mapper.selectByVersion(versionId.value()))
                .map(this::toDomain);
    }

    @Override
    public void save(Initiative initiative) {
        InitiativeData data = toData(initiative);
        if (initiative.getId() == null) {
            if (mapper.insert(data) != 1) throw new DomainException("立项新增失败");
            initiative.assignIdentity(InitiativeId.of(data.getId()));
        } else if (mapper.update(data) != 1) {
            throw new DomainException("立项更新失败：" + initiative.getId().value());
        }
    }

    private Initiative toDomain(InitiativeData data) {
        return Initiative.restore(
                InitiativeId.of(data.getId()),
                ProjectId.of(data.getProjectId()),
                data.getVersionId() == null ? null : ProductVersionId.of(data.getVersionId()),
                UserId.of(data.getCreatedBy()),
                data.getTitle(),
                data.getBusinessGoal(),
                data.getScopeText(),
                data.getOutOfScopeText(),
                data.getSuccessMetrics(),
                data.getStakeholders(),
                data.getTargetVersion(),
                data.getRiskText(),
                data.getMeetingMinutes(),
                InitiativeStatus.valueOf(data.getStatus()),
                DeliveryTime.fromDb(data.getCreatedAt()),
                DeliveryTime.fromDb(data.getUpdatedAt()),
                DeliveryTime.fromDb(data.getSubmittedAt()),
                DeliveryTime.fromDb(data.getApprovedAt()),
                DeliveryTime.fromDb(data.getRejectedAt()));
    }

    private InitiativeData toData(Initiative initiative) {
        InitiativeData data = new InitiativeData();
        data.setId(initiative.getId() == null ? null : initiative.getId().value());
        data.setProjectId(initiative.getProjectId().value());
        data.setVersionId(initiative.getVersionId() == null
                ? null
                : initiative.getVersionId().value());
        data.setCreatedBy(initiative.getCreatedBy().value());
        data.setTitle(initiative.getTitle());
        data.setBusinessGoal(initiative.getBusinessGoal());
        data.setScopeText(initiative.getScopeText());
        data.setOutOfScopeText(initiative.getOutOfScopeText());
        data.setSuccessMetrics(initiative.getSuccessMetrics());
        data.setStakeholders(initiative.getStakeholders());
        data.setTargetVersion(initiative.getTargetVersion());
        data.setRiskText(initiative.getRiskText());
        data.setMeetingMinutes(initiative.getMeetingMinutes());
        data.setStatus(initiative.getStatus().name());
        data.setSubmittedAt(DeliveryTime.toDb(initiative.getSubmittedAt()));
        data.setApprovedAt(DeliveryTime.toDb(initiative.getApprovedAt()));
        data.setRejectedAt(DeliveryTime.toDb(initiative.getRejectedAt()));
        data.setCreatedAt(DeliveryTime.toDb(initiative.getCreatedAt()));
        data.setUpdatedAt(DeliveryTime.toDb(initiative.getUpdatedAt()));
        return data;
    }
}
