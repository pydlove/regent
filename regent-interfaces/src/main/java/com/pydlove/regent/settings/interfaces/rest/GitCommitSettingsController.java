package com.pydlove.regent.settings.interfaces.rest;

import com.pydlove.regent.settings.application.command.GitCommitSettingsService;
import com.pydlove.regent.settings.interfaces.dto.GitCommitSettingsResponse;
import com.pydlove.regent.settings.interfaces.dto.UpdateGitCommitSettingsRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 企业 Git 提交规范的独立设置分类。 */
@RestController
@RequestMapping("/api/settings/git-commit")
public class GitCommitSettingsController {

    private final GitCommitSettingsService service;

    public GitCommitSettingsController(GitCommitSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public GitCommitSettingsResponse current() {
        return GitCommitSettingsResponse.from(service.current());
    }

    @PutMapping
    public GitCommitSettingsResponse update(@RequestBody UpdateGitCommitSettingsRequest request) {
        return GitCommitSettingsResponse.from(service.update(
                request.enabled(),
                request.defaultType(),
                request.defaultScope(),
                request.subjectTemplate(),
                request.bodyTemplate(),
                request.validationPattern(),
                request.validationHint()));
    }
}
