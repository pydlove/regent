package com.pydlove.regent.project.application.query;

/** 项目本次发布可使用的版本解析结果。 */
public record ProjectVersionView(
        String version,
        String source,
        boolean available,
        String message) {

    public ProjectVersionView {
        version = version == null || version.isBlank() ? null : version.strip();
        source = source == null || source.isBlank() ? "NONE" : source.strip();
        message = message == null ? "" : message.strip();
    }
}
