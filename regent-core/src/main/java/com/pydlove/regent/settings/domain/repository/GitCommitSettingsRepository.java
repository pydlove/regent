package com.pydlove.regent.settings.domain.repository;

import com.pydlove.regent.settings.domain.model.GitCommitSettings;

import java.util.Optional;

/** 企业 Git 提交规范的持久化入口。 */
public interface GitCommitSettingsRepository {

    Optional<GitCommitSettings> find();

    void save(GitCommitSettings settings);
}
