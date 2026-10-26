package com.pydlove.regent.execution.application.command;

import java.util.List;

/** 跨项目复制结果；跳过项必须带原因，禁止静默丢失。 */
public record CodeStandardCopyResult(List<String> copied, List<Skipped> skipped) {

    public CodeStandardCopyResult {
        copied = copied == null ? List.of() : List.copyOf(copied);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }

    public record Skipped(String name, String reason) {
    }
}
