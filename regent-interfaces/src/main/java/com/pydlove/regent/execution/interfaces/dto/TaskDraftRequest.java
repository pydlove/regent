package com.pydlove.regent.execution.interfaces.dto;

import java.util.List;

/** 自动保存任务草稿的完整表单快照。 */
public record TaskDraftRequest(
        Long projectId,
        String prompt,
        Integer priority,
        List<Long> pluginIds) {
}
