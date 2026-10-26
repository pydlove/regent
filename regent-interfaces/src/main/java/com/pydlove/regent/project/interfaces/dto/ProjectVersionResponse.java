package com.pydlove.regent.project.interfaces.dto;

import com.pydlove.regent.project.application.query.ProjectVersionView;

/** 项目本次可解析出的发布版本。 */
public record ProjectVersionResponse(
        String version,
        String source,
        boolean available,
        String message) {

    public static ProjectVersionResponse from(ProjectVersionView view) {
        return new ProjectVersionResponse(
                view.version(), view.source(), view.available(), view.message());
    }
}
