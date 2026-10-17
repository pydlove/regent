package com.pydlove.regent.plugin.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** 任务、插件版本与当前启停状态的联合读模型。 */
@Data
public class PluginBindingRow {

    private Long pluginId;
    private String pluginName;
    private Integer revision;
    private String filename;
    private String contentHash;
    private String content;
    private Boolean enabled;
    private LocalDateTime boundAt;
}
