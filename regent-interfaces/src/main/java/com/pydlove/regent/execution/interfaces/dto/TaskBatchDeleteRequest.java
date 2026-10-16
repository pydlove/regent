package com.pydlove.regent.execution.interfaces.dto;

import java.util.List;

/** 任务列表的批量删除请求。 */
public record TaskBatchDeleteRequest(List<Long> taskIds) {
}
