package com.pydlove.regent.settings.application.command;

import com.pydlove.regent.settings.domain.model.GitCommitSettings;
import com.pydlove.regent.settings.domain.repository.GitCommitSettingsRepository;

/** 读取与更新企业 Git 提交规范。 */
public class GitCommitSettingsService {

    private final GitCommitSettingsRepository repository;

    public GitCommitSettingsService(GitCommitSettingsRepository repository) {
        this.repository = repository;
    }

    public GitCommitSettings current() {
        return repository.find().orElseGet(GitCommitSettings::defaults);
    }

    public GitCommitSettings update(boolean enabled,
                                    String defaultType,
                                    String defaultScope,
                                    String subjectTemplate,
                                    String bodyTemplate,
                                    String validationPattern,
                                    String validationHint) {
        GitCommitSettings settings = new GitCommitSettings(
                enabled,
                defaultType,
                defaultScope,
                subjectTemplate,
                bodyTemplate,
                validationPattern,
                validationHint);
        repository.save(settings);
        return settings;
    }
}
