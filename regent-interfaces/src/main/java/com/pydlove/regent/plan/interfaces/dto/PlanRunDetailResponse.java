package com.pydlove.regent.plan.interfaces.dto;

import com.pydlove.regent.plan.application.query.PlanRunDetailView;

import java.util.List;

/** 一次计划执行及其逐任务结果。 */
public record PlanRunDetailResponse(
        PlanRunResponse run,
        List<PlanRunItemResponse> items) {

    public static PlanRunDetailResponse from(PlanRunDetailView view) {
        return new PlanRunDetailResponse(
                PlanRunResponse.from(view.run()),
                view.items().stream().map(PlanRunItemResponse::from).toList());
    }
}
