package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.SaveTaskDraftCommand;
import com.pydlove.regent.execution.application.command.TaskDraftService;
import com.pydlove.regent.execution.domain.model.TaskDraft;
import com.pydlove.regent.execution.interfaces.dto.CurrentTaskDraftResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskDraftRequest;
import com.pydlove.regent.execution.interfaces.dto.TaskDraftResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.project.domain.model.ProjectId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 当前登录用户的任务草稿接口。 */
@RestController
@RequestMapping("/api/task-drafts")
public class TaskDraftController {

    private final TaskDraftService taskDraftService;

    public TaskDraftController(TaskDraftService taskDraftService) {
        this.taskDraftService = taskDraftService;
    }

    @GetMapping("/current")
    public CurrentTaskDraftResponse current(HttpServletRequest request) {
        return new CurrentTaskDraftResponse(
                taskDraftService.current(currentUser(request))
                        .map(TaskDraftResponse::from)
                        .orElse(null));
    }

    @PutMapping("/current")
    public TaskDraftResponse save(@RequestBody TaskDraftRequest request,
                                  HttpServletRequest servletRequest) {
        TaskDraft draft = taskDraftService.save(
                currentUser(servletRequest),
                new SaveTaskDraftCommand(
                        request.projectId() == null ? null : ProjectId.of(request.projectId()),
                        request.prompt(),
                        request.priority() == null ? 0 : request.priority(),
                        pluginIds(request.pluginIds())));
        return TaskDraftResponse.from(draft);
    }

    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request) {
        taskDraftService.delete(currentUser(request));
    }

    private static UserId currentUser(HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return UserId.of(user.id());
    }

    private static List<PluginId> pluginIds(List<Long> pluginIds) {
        if (pluginIds == null) {
            return List.of();
        }
        if (pluginIds.contains(null)) {
            throw new IllegalArgumentException("插件 ID 不能为空");
        }
        return pluginIds.stream().map(PluginId::of).toList();
    }
}
