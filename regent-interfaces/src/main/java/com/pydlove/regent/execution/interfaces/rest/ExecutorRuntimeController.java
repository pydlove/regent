package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.ExecutorInstallService;
import com.pydlove.regent.execution.application.query.ExecutorRuntimeQueryService;
import com.pydlove.regent.execution.interfaces.dto.CodexInstallRequest;
import com.pydlove.regent.execution.interfaces.dto.CodexInstallResponse;
import com.pydlove.regent.execution.interfaces.dto.CodexRuntimeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 设置页的运行时自检接口。 */
@RestController
@RequestMapping("/api/execution/codex")
public class ExecutorRuntimeController {

    private final ExecutorRuntimeQueryService executorRuntimeQueryService;
    private final ExecutorInstallService executorInstallService;

    public ExecutorRuntimeController(ExecutorRuntimeQueryService executorRuntimeQueryService,
                                     ExecutorInstallService executorInstallService) {
        this.executorRuntimeQueryService = executorRuntimeQueryService;
        this.executorInstallService = executorInstallService;
    }

    @GetMapping
    public CodexRuntimeResponse check() {
        return CodexRuntimeResponse.from(executorRuntimeQueryService.check());
    }

    @PostMapping("/install")
    public CodexInstallResponse install(@RequestBody CodexInstallRequest request) {
        return CodexInstallResponse.from(executorInstallService.install(request.method()));
    }
}
