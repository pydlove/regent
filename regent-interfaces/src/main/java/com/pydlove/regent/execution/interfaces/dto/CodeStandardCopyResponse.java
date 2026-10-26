package com.pydlove.regent.execution.interfaces.dto;

import com.pydlove.regent.execution.application.command.CodeStandardCopyResult;

import java.util.List;

/** 复制结果。 */
public record CodeStandardCopyResponse(List<String> copied, List<Skipped> skipped) {

    public static CodeStandardCopyResponse from(CodeStandardCopyResult result) {
        return new CodeStandardCopyResponse(
                result.copied(),
                result.skipped().stream()
                        .map(item -> new Skipped(item.name(), item.reason()))
                        .toList());
    }

    public record Skipped(String name, String reason) {
    }
}
