package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.project.domain.model.Project;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 领域对象 ↔ 数据对象的转换。静态工具而不是 Bean：转换没有依赖、没有状态。
 */
public final class ProjectDataMapper {

    private ProjectDataMapper() {
    }

    public static ProjectData toData(Project project) {
        ProjectData data = new ProjectData();
        data.setId(project.getId() == null ? null : project.getId().value());
        data.setCode(project.getCode());
        data.setName(project.getName());
        data.setPath(project.getPath());
        data.setTechStack(project.getTechStack());
        data.setVerifyMethod(project.getVerifyMethod());
        data.setDefaultDecisionLevel(project.getDefaultDecisionLevel().name());
        data.setCreatedAt(toDb(project.getCreatedAt()));
        data.setUpdatedAt(toDb(project.getUpdatedAt()));
        return data;
    }

    /** UTC 瞬时 → DATETIME 字面量。DATETIME 里没有时区，约定它存的一律是 UTC。 */
    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
