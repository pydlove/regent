package com.pydlove.regent.settings.infrastructure.persistence;

import com.pydlove.regent.settings.domain.model.GitCommitSettings;
import com.pydlove.regent.settings.domain.repository.GitCommitSettingsRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** 企业 Git 提交规范单行表的仓储实现。 */
public class MyBatisGitCommitSettingsRepository implements GitCommitSettingsRepository {

    private static final int SETTINGS_ID = 1;

    private final GitCommitSettingsMapper mapper;

    public MyBatisGitCommitSettingsRepository(GitCommitSettingsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<GitCommitSettings> find() {
        return Optional.ofNullable(mapper.select())
                .map(row -> new GitCommitSettings(
                        Boolean.TRUE.equals(row.getEnabled()),
                        row.getDefaultType(),
                        row.getDefaultScope(),
                        row.getSubjectTemplate(),
                        row.getBodyTemplate(),
                        row.getValidationPattern(),
                        row.getValidationHint()));
    }

    @Override
    public void save(GitCommitSettings settings) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        mapper.upsert(
                SETTINGS_ID,
                settings.enabled(),
                settings.defaultType(),
                settings.defaultScope(),
                settings.subjectTemplate(),
                settings.bodyTemplate(),
                settings.validationPattern(),
                settings.validationHint(),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC));
    }
}
