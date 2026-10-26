package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code code_standard} 数据对象。 */
@Data
public class CodeStandardData {
    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private String scope;
    private Boolean enabled;
    private Boolean defaultApply;
    private Integer sortOrder;
    private Boolean archived;
    private Long sourceProjectId;
    private Long sourceStandardId;
    private Long sourceVersionId;
    private Integer sourceVersionNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
