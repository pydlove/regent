package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** initiative_plan_item 表读写对象。 */
@Data
public class InitiativePlanItemData {
    private Long id;
    private Long initiativeId;
    private String name;
    private String roleCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer headcount;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
