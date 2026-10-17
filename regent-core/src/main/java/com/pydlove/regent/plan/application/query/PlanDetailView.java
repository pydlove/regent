package com.pydlove.regent.plan.application.query;

import java.util.List;

/** 计划详情：聚合摘要 + 纳管任务 + 执行历史。 */
public record PlanDetailView(
        PlanSummaryView plan,
        List<PlanTaskView> tasks,
        List<PlanRunView> runs) {
}
