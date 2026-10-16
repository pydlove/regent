package com.pydlove.regent.execution.interfaces.dto;

/** 编辑任务的请求体。标题仍由指令首行推导，不接受客户端单独覆写。 */
public record TaskUpdateRequest(
        String prompt,
        Integer priority) {
}
