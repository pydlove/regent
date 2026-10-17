package com.pydlove.regent.plugin.domain.repository;

import com.pydlove.regent.plugin.domain.model.Plugin;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.domain.model.PluginVersion;

import java.util.List;
import java.util.Optional;

/** 插件聚合仓储。 */
public interface PluginRepository {

    Optional<Plugin> findById(PluginId id);

    /** 修改插件时锁住聚合并按最新版本生成下一版。 */
    Optional<Plugin> findByIdForUpdate(PluginId id);

    /** 启动同步内置插件时锁住聚合并读取当前版本。 */
    Optional<Plugin> findByBuiltinKeyForUpdate(String builtinKey);

    Optional<PluginVersion> findVersion(PluginId id, int revision);

    List<Plugin> findAll();

    boolean existsByName(String name, PluginId excludingId);

    void save(Plugin plugin);
}
