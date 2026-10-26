package com.pydlove.regent.project.interfaces.dto;

import com.pydlove.regent.project.domain.repository.ProjectProfile;

import java.time.Instant;

/**
 * 项目的对外契约。字段与 {@link ProjectProfile} 一一对应，不做改名 ——
 * 前端 {@code types/api.ts} 里的 ProjectResponse 逐字对齐这两个名字。
 *
 * <p>不含 {@code techStack} / {@code verifyMethod}：画像行里现在没有这两列（见 ProjectProfileRow），
 * 等真有展示或编辑入口时，请求、画像、响应三处一起加，不允许只加一处。
 */
public record ProjectResponse(
        long id,
        Long organizationId,
        String code,
        String name,
        String path,
        String defaultDecisionLevel,
        String versionSource,
        String fallbackVersion,
        boolean enabled,
        String summaryStatus,
        String summary,
        String summaryError,
        Instant summaryGeneratedAt) {

    public static ProjectResponse from(ProjectProfile profile) {
        return new ProjectResponse(
                profile.id().value(),
                profile.organizationId() == null ? null : profile.organizationId().value(),
                profile.code(),
                profile.name(),
                profile.path(),
                profile.defaultDecisionLevel().name(),
                profile.versionSource().name(),
                profile.fallbackVersion(),
                profile.enabled(),
                profile.summaryStatus().name(),
                profile.summary(),
                profile.summaryError(),
                profile.summaryGeneratedAt());
    }
}
