package com.pydlove.regent.delivery.infrastructure.persistence;

import com.pydlove.regent.delivery.domain.model.DesignModuleStatus;
import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModule;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModuleId;
import com.pydlove.regent.delivery.domain.repository.RequirementDesignModuleRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisRequirementDesignModuleRepository
        implements RequirementDesignModuleRepository {

    private final RequirementDesignModuleMapper mapper;

    public MyBatisRequirementDesignModuleRepository(RequirementDesignModuleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RequirementDesignModule> findByDelivery(RequirementDeliveryId deliveryId) {
        return mapper.selectByDelivery(deliveryId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<RequirementDesignModule> findById(RequirementDesignModuleId id) {
        return Optional.ofNullable(mapper.selectById(id.value())).map(this::toDomain);
    }

    @Override
    public Optional<RequirementDesignModule> findByIdAndDelivery(
            RequirementDesignModuleId id, RequirementDeliveryId deliveryId) {
        return Optional.ofNullable(
                        mapper.selectByIdAndDelivery(id.value(), deliveryId.value()))
                .map(this::toDomain);
    }

    @Override
    public RequirementDesignModule save(RequirementDesignModule module) {
        RequirementDesignModuleData data = toData(module);
        if (module.getId() == null) {
            if (mapper.insert(data) != 1) {
                throw new DomainException("设计模块保存失败");
            }
            module.assignIdentity(RequirementDesignModuleId.of(data.getId()));
        } else if (mapper.update(data) != 1) {
            throw new DomainException("设计模块更新失败：" + module.getId().value());
        }
        return module;
    }

    @Override
    public void delete(RequirementDesignModuleId id) {
        mapper.delete(id.value());
    }

    @Override
    public void reopenAll(RequirementDeliveryId deliveryId) {
        mapper.reopenAll(deliveryId.value());
    }

    private RequirementDesignModule toDomain(RequirementDesignModuleData data) {
        return RequirementDesignModule.restore(
                RequirementDesignModuleId.of(data.getId()),
                RequirementDeliveryId.of(data.getDeliveryId()),
                data.getName(),
                data.getDescription(),
                UserId.of(data.getAssigneeId()),
                DesignModuleStatus.valueOf(data.getStatus()),
                data.getDesignSummary(),
                data.getDesignContent(),
                UserId.of(data.getCreatedBy()),
                DeliveryTime.fromDb(data.getCreatedAt()),
                DeliveryTime.fromDb(data.getUpdatedAt()));
    }

    private RequirementDesignModuleData toData(RequirementDesignModule module) {
        RequirementDesignModuleData data = new RequirementDesignModuleData();
        data.setId(module.getId() == null ? null : module.getId().value());
        data.setDeliveryId(module.getDeliveryId().value());
        data.setName(module.getName());
        data.setDescription(module.getDescription());
        data.setAssigneeId(module.getAssigneeId().value());
        data.setStatus(module.getStatus().name());
        data.setDesignSummary(module.getDesignSummary());
        data.setDesignContent(module.getDesignContent());
        data.setCreatedBy(module.getCreatedBy().value());
        data.setCreatedAt(DeliveryTime.toDb(module.getCreatedAt()));
        data.setUpdatedAt(DeliveryTime.toDb(module.getUpdatedAt()));
        return data;
    }
}
