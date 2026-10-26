package com.pydlove.regent.execution.interfaces.dto;

import java.util.List;

/** 从一个项目复制开发规范到目标项目。 */
public record CodeStandardCopyRequest(
        long sourceProjectId,
        List<Long> standardIds,
        String strategy) {
}
