package com.pydlove.regent.project.infrastructure.persistence;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.shared.kernel.DecisionLevel;

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
        data.setOwnerId(project.getOwnerId() == null ? null : project.getOwnerId().value());
        data.setCode(project.getCode());
        data.setName(project.getName());
        data.setPath(project.getPath());
        data.setTechStack(project.getTechStack());
        data.setVerifyMethod(project.getVerifyMethod());
        data.setDefaultDecisionLevel(project.getDefaultDecisionLevel().name());
        data.setEnabled(project.isEnabled());
        data.setSummaryStatus(project.getSummaryStatus().name());
        data.setSummary(project.getSummary());
        data.setSummaryError(project.getSummaryError());
        data.setSummaryGeneratedAt(toDb(project.getSummaryGeneratedAt()));
        data.setCreatedAt(toDb(project.getCreatedAt()));
        data.setUpdatedAt(toDb(project.getUpdatedAt()));
        return data;
    }

    public static Project toDomain(ProjectData data) {
        return Project.restore(
                ProjectId.of(data.getId()),
                data.getOwnerId() == null ? null : UserId.of(data.getOwnerId()),
                data.getCode(),
                data.getName(),
                data.getPath(),
                data.getTechStack(),
                data.getVerifyMethod(),
                DecisionLevel.parse(data.getDefaultDecisionLevel()),
                Boolean.TRUE.equals(data.getEnabled()),
                ProjectSummaryStatus.parse(data.getSummaryStatus()),
                data.getSummary(),
                data.getSummaryError(),
                toInstant(data.getSummaryGeneratedAt()),
                toInstant(data.getCreatedAt()),
                toInstant(data.getUpdatedAt()));
    }

    /** UTC 瞬时 → DATETIME 字面量。DATETIME 里没有时区，约定它存的一律是 UTC。 */
    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
