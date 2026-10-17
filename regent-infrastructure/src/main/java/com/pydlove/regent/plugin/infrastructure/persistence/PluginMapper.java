package com.pydlove.regent.plugin.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 插件聚合与任务绑定映射接口，SQL 在 {@code mapper/PluginMapper.xml}。 */
public interface PluginMapper {

    PluginRow selectById(@Param("id") long id);

    PluginRow selectByIdForUpdate(@Param("id") long id);

    List<PluginRow> selectAll();

    PluginVersionRow selectVersion(@Param("pluginId") long pluginId,
                                   @Param("revision") int revision);

    int countByName(@Param("name") String name,
                    @Param("excludingId") Long excludingId);

    int insertPlugin(PluginData data);

    int updatePlugin(PluginData data);

    int insertVersion(PluginVersionRow data);

    List<PluginBindingRow> selectBindingsByTask(@Param("taskId") long taskId);

    int deleteBindingsByTask(@Param("taskId") long taskId);

    int insertBinding(@Param("taskId") long taskId,
                      @Param("pluginId") long pluginId,
                      @Param("revision") int revision,
                      @Param("boundAt") java.time.LocalDateTime boundAt);
}
