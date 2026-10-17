package com.pydlove.regent.plugin.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code plugin} 表写侧数据对象。 */
@Data
public class PluginData {

    private Long id;
    private String name;
    private String description;
    private String tags;
    private Boolean enabled;
    private Integer currentRevision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
