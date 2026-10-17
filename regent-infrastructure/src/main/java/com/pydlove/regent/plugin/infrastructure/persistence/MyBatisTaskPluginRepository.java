package com.pydlove.regent.plugin.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.plugin.domain.model.PluginBinding;
import com.pydlove.regent.plugin.domain.repository.TaskPluginRepository;
import com.pydlove.regent.shared.domain.DomainException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** {@link TaskPluginRepository} 的 MyBatis 实现。 */
@Repository
public class MyBatisTaskPluginRepository implements TaskPluginRepository {

    private final PluginMapper mapper;

    public MyBatisTaskPluginRepository(PluginMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<PluginBinding> findByTask(TaskId taskId) {
        return mapper.selectBindingsByTask(taskId.value()).stream()
                .map(PluginDataMapper::toBinding)
                .toList();
    }

    @Override
    public void replace(TaskId taskId, List<PluginBinding> bindings) {
        mapper.deleteBindingsByTask(taskId.value());
        for (PluginBinding binding : bindings) {
            int inserted = mapper.insertBinding(
                    taskId.value(),
                    binding.pluginId().value(),
                    binding.revision(),
                    LocalDateTime.ofInstant(binding.boundAt(), ZoneOffset.UTC));
            if (inserted != 1) {
                throw new DomainException("任务插件绑定失败：" + binding.pluginName());
            }
        }
    }
}
