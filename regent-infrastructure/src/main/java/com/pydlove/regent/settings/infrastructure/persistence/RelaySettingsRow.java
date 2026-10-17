package com.pydlove.regent.settings.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code relay_setting} 单行表的数据对象。 */
@Data
public class RelaySettingsRow {

    private Integer id;
    private String serverUrl;
    private String serverIp;
    private String sshUsername;
    private String installDirectory;
    private Boolean monitoringEnabled;
    private LocalDateTime updatedAt;
}
