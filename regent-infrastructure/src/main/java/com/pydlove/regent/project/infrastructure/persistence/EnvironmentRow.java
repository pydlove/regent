package com.pydlove.regent.project.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code environment_entry} 表的读侧数据对象。 */
@Data
public class EnvironmentRow {

    private Long id;
    private Long projectId;
    private String itemKey;
    private String itemValue;
    private String tags;
    private String description;
    private Boolean secret;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
