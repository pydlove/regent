package com.pydlove.regent.plugin.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code plugin_version} 表数据对象。 */
@Data
public class PluginVersionRow {

    private Long pluginId;
    private Integer revision;
    private String filename;
    private String content;
    private String contentHash;
    private LocalDateTime createdAt;
}
