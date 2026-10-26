package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** requirement_design_module 表读写对象。 */
@Data
public class RequirementDesignModuleData {
    private Long id;
    private Long deliveryId;
    private String name;
    private String description;
    private Long assigneeId;
    private String status;
    private String designSummary;
    private String designContent;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
