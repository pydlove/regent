package com.pydlove.regent.execution.interfaces.dto;

/**
 * 人工录入任务的请求体。
 *
 * <p>没有 {@code title}：任务的标题由后端从指令首行推导，前端不必（也不该）自己造一个 ——
 * 两处各造一次，列表里就会出现「标题与指令说的不是一回事」的记录。
 *
 * <p>{@code priority} 可空，为空按 0 处理：它只是排队顺序，默认值没有风险。
 * 这一点和项目阈值相反 —— 那个决定「谁有权替人拍板」，必须由调用方显式表态。
 */
public record TaskCreateRequest(
        Long projectId,
        String prompt,
        Integer priority) {
}
