package com.pydlove.regent.project.interfaces.dto;

import com.pydlove.regent.project.domain.model.CodeEditorDescriptor;

public record CodeEditorResponse(
        String id,
        String name,
        boolean supportsFileNavigation) {

    public static CodeEditorResponse from(CodeEditorDescriptor descriptor) {
        return new CodeEditorResponse(
                descriptor.id().name(),
                descriptor.name(),
                descriptor.supportsFileNavigation());
    }
}
