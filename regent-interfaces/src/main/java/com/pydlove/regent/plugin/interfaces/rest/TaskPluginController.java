package com.pydlove.regent.plugin.interfaces.rest;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.plugin.application.command.TaskPluginService;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.interfaces.dto.BindTaskPluginsRequest;
import com.pydlove.regent.plugin.interfaces.dto.TaskPluginResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 任务插件绑定接口。 */
@RestController
@RequestMapping("/api/tasks/{taskId}/plugins")
public class TaskPluginController {

    private final TaskPluginService taskPluginService;

    public TaskPluginController(TaskPluginService taskPluginService) {
        this.taskPluginService = taskPluginService;
    }

    @GetMapping
    public List<TaskPluginResponse> list(@PathVariable long taskId) {
        return taskPluginService.list(TaskId.of(taskId)).stream()
                .map(TaskPluginResponse::from)
                .toList();
    }

    @PutMapping
    public List<TaskPluginResponse> bind(@PathVariable long taskId,
                                         @RequestBody BindTaskPluginsRequest request) {
        if (request.pluginIds() == null) {
            throw new IllegalArgumentException("缺少 pluginIds");
        }
        return taskPluginService.bind(
                        TaskId.of(taskId),
                        request.pluginIds().stream().map(PluginId::of).toList())
                .stream()
                .map(TaskPluginResponse::from)
                .toList();
    }
}
