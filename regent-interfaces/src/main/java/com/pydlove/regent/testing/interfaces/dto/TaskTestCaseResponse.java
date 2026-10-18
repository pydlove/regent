package com.pydlove.regent.testing.interfaces.dto;

import com.pydlove.regent.testing.domain.model.TestCaseBinding;

import java.time.Instant;

/** 任务绑定的测试用例摘要。 */
public record TaskTestCaseResponse(
        long testCaseId,
        String name,
        String type,
        String command,
        String scriptFilename,
        boolean enabled,
        Instant boundAt) {

    public static TaskTestCaseResponse from(TestCaseBinding binding) {
        return new TaskTestCaseResponse(
                binding.testCaseId().value(),
                binding.name(),
                binding.type().name(),
                binding.command(),
                binding.scriptFilename(),
                binding.enabled() && !binding.deleted(),
                binding.boundAt());
    }
}
