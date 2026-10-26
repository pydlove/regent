package com.pydlove.regent.settings.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 企业 Git 提交规范单行表的 MyBatis 映射接口。 */
public interface GitCommitSettingsMapper {

    GitCommitSettingsRow select();

    int upsert(@Param("id") int id,
               @Param("enabled") boolean enabled,
               @Param("defaultType") String defaultType,
               @Param("defaultScope") String defaultScope,
               @Param("subjectTemplate") String subjectTemplate,
               @Param("bodyTemplate") String bodyTemplate,
               @Param("validationPattern") String validationPattern,
               @Param("validationHint") String validationHint,
               @Param("updatedAt") LocalDateTime updatedAt);
}
