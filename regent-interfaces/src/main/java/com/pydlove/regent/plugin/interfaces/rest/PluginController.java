package com.pydlove.regent.plugin.interfaces.rest;

import com.pydlove.regent.plugin.application.command.PluginCommandService;
import com.pydlove.regent.plugin.application.command.SavePluginCommand;
import com.pydlove.regent.plugin.application.query.PluginQueryService;
import com.pydlove.regent.plugin.domain.model.PluginId;
import com.pydlove.regent.plugin.interfaces.dto.PluginDetailResponse;
import com.pydlove.regent.plugin.interfaces.dto.PluginEnabledRequest;
import com.pydlove.regent.plugin.interfaces.dto.PluginSaveRequest;
import com.pydlove.regent.plugin.interfaces.dto.PluginSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 插件库管理接口。 */
@RestController
@RequestMapping("/api/plugins")
public class PluginController {

    private final PluginCommandService commandService;
    private final PluginQueryService queryService;

    public PluginController(PluginCommandService commandService, PluginQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<PluginSummaryResponse> list() {
        return queryService.list().stream().map(PluginSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PluginDetailResponse detail(@PathVariable long id) {
        return PluginDetailResponse.from(queryService.get(PluginId.of(id)));
    }

    @PostMapping
    public PluginDetailResponse create(@RequestBody PluginSaveRequest request) {
        return PluginDetailResponse.from(commandService.create(toCommand(request)));
    }

    @PutMapping("/{id}")
    public PluginDetailResponse update(@PathVariable long id, @RequestBody PluginSaveRequest request) {
        return PluginDetailResponse.from(
                commandService.update(PluginId.of(id), toCommand(request)));
    }

    @PutMapping("/{id}/enabled")
    public PluginDetailResponse setEnabled(@PathVariable long id,
                                           @RequestBody PluginEnabledRequest request) {
        if (request.enabled() == null) {
            throw new IllegalArgumentException("缺少 enabled");
        }
        return PluginDetailResponse.from(
                commandService.setEnabled(PluginId.of(id), request.enabled()));
    }

    private static SavePluginCommand toCommand(PluginSaveRequest request) {
        return new SavePluginCommand(
                request.name(),
                request.description(),
                request.tags(),
                request.filename(),
                request.content(),
                request.enabled());
    }
}
