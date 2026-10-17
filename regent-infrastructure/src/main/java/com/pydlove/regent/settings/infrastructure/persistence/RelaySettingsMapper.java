package com.pydlove.regent.settings.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 中继设置单行表的 MyBatis 映射接口。SQL 在 {@code mapper/RelaySettingsMapper.xml}。 */
public interface RelaySettingsMapper {

    RelaySettingsRow select();

    int upsert(@Param("id") int id,
               @Param("serverUrl") String serverUrl,
               @Param("serverIp") String serverIp,
               @Param("sshUsername") String sshUsername,
               @Param("installDirectory") String installDirectory,
               @Param("monitoringEnabled") boolean monitoringEnabled,
               @Param("updatedAt") LocalDateTime updatedAt);
}
