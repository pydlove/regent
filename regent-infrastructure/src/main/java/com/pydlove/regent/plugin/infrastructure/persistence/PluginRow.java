package com.pydlove.regent.plugin.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code plugin} 表读侧数据对象。 */
@Data
public class PluginRow {

    private Long id;
    private String builtinKey;
    private String name;
    private String description;
    private String tags;
    private Boolean enabled;
    private Integer currentRevision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
