package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.ExecutionSettings;

import java.util.Optional;

/** 平台级执行设置的持久化入口。未配置时返回空，由应用层采用启动默认值。 */
public interface ExecutionSettingsRepository {

    Optional<ExecutionSettings> find();

    void save(ExecutionSettings settings);
}
