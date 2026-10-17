package com.pydlove.regent.settings.domain.repository;

import com.pydlove.regent.settings.domain.model.RelaySettings;

import java.util.Optional;

/** 中继连接、安装目录与状态监听设置的持久化入口。未保存过设置时返回空。 */
public interface RelaySettingsRepository {

    Optional<RelaySettings> find();

    void save(RelaySettings settings);
}
