package com.pydlove.regent.settings.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code git_commit_setting} 单行表的数据对象。 */
@Data
public class GitCommitSettingsRow {

    private Integer id;
    private Boolean enabled;
    private String defaultType;
    private String defaultScope;
    private String subjectTemplate;
    private String bodyTemplate;
    private String validationPattern;
    private String validationHint;
    private LocalDateTime updatedAt;
}
