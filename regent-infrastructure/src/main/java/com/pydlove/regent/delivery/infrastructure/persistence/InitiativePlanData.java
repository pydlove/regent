package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** initiative_plan 表读写对象。 */
@Data
public class InitiativePlanData {
    private Long initiativeId;
    private LocalDate planStartDate;
    private LocalDate planEndDate;
    private String milestones;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
