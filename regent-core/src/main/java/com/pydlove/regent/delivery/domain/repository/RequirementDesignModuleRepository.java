package com.pydlove.regent.delivery.domain.repository;

import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModule;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModuleId;

import java.util.List;
import java.util.Optional;

/** 模块详细设计与研发负责人仓储。 */
public interface RequirementDesignModuleRepository {

    List<RequirementDesignModule> findByDelivery(RequirementDeliveryId deliveryId);

    Optional<RequirementDesignModule> findById(RequirementDesignModuleId id);

    Optional<RequirementDesignModule> findByIdAndDelivery(
            RequirementDesignModuleId id, RequirementDeliveryId deliveryId);

    RequirementDesignModule save(RequirementDesignModule module);

    void delete(RequirementDesignModuleId id);

    void reopenAll(RequirementDeliveryId deliveryId);
}
