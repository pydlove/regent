package com.pydlove.regent.plan.interfaces.dto;

import com.pydlove.regent.plan.application.query.PlanDetailView;

import java.util.List;

/** 计划详情响应。 */
public record PlanDetailResponse(
        PlanSummaryResponse plan,
        List<PlanTaskResponse> tasks,
        List<PlanRunResponse> runs) {

    public static PlanDetailResponse from(PlanDetailView view) {
        return new PlanDetailResponse(
                PlanSummaryResponse.from(view.plan()),
                view.tasks().stream().map(PlanTaskResponse::from).toList(),
                view.runs().stream().map(PlanRunResponse::from).toList());
    }
}
