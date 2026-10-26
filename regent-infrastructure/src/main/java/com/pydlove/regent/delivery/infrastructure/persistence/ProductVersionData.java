package com.pydlove.regent.delivery.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** product_version 表读写对象。 */
@Data
public class ProductVersionData {
    private Long id;
    private Long projectId;
    private String versionNo;
    private String name;
    private String description;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
