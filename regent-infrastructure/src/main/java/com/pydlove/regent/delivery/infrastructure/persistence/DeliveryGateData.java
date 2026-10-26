package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeliveryGateData {
    private Long id;
    private String scopeType;
    private Long scopeId;
    private String gateType;
    private String decision;
    private String roleCode;
    private String comment;
    private Long decidedBy;
    private LocalDateTime decidedAt;
}
