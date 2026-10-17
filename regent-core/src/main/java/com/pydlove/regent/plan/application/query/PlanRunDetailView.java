package com.pydlove.regent.plan.application.query;

import java.util.List;

/** 一次计划执行及其逐任务结果。 */
public record PlanRunDetailView(PlanRunView run, List<PlanRunItemView> items) {
}
