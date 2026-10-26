package com.pydlove.regent.project.domain.model;

/** 本机已安装且允许调用的代码编辑器。 */
public record CodeEditorDescriptor(
        CodeEditorId id,
        String name,
        boolean supportsFileNavigation) {
}
