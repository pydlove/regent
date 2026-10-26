package com.pydlove.regent.delivery.domain.repository;

import com.pydlove.regent.delivery.domain.model.RequirementDeliveryId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachment;
import com.pydlove.regent.delivery.domain.model.RequirementDesignAttachmentId;
import com.pydlove.regent.delivery.domain.model.RequirementDesignModuleId;

import java.util.List;
import java.util.Optional;

/** 概要设计与模块详细设计附件仓储。 */
public interface RequirementDesignAttachmentRepository {

    RequirementDesignAttachment save(RequirementDesignAttachment attachment);

    Optional<RequirementDesignAttachment> findById(RequirementDesignAttachmentId id);

    List<RequirementDesignAttachment> findByDelivery(RequirementDeliveryId deliveryId);

    List<RequirementDesignAttachment> findByModule(RequirementDesignModuleId moduleId);
}
