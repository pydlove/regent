package com.pydlove.regent.plugin.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.plugin.domain.model.PluginBinding;

import java.util.List;

/** 任务绑定仓储。绑定保存插件版本，后续插件升级不会改写已有任务输入。 */
public interface TaskPluginRepository {

    List<PluginBinding> findByTask(TaskId taskId);

    void replace(TaskId taskId, List<PluginBinding> bindings);
}
