package com.pydlove.regent.execution.interfaces.dto;

import java.util.List;

/** 任务列表的批量派发请求。 */
public record TaskBatchDispatchRequest(List<Long> taskIds) {
}
