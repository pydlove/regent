package com.pydlove.regent.delivery.application.query;

import com.pydlove.regent.delivery.domain.model.InitiativePlan;
import com.pydlove.regent.delivery.domain.model.InitiativePlanItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 版本计划读模型，附带自动汇总的人天和分角色工作量。 */
public record InitiativePlanView(
        InitiativePlan plan,
        int totalWorkloadDays,
        List<RoleWorkload> roleWorkloads) {

    public static InitiativePlanView from(InitiativePlan plan) {
        Map<String, Integer> byRole = new LinkedHashMap<>();
        for (InitiativePlanItem item : plan.items()) {
            byRole.merge(item.roleCode(), item.workloadDays(), Integer::sum);
        }
        return new InitiativePlanView(
                plan,
                plan.totalWorkloadDays(),
                byRole.entrySet().stream()
                        .map(entry -> new RoleWorkload(entry.getKey(), entry.getValue()))
                        .toList());
    }

    public record RoleWorkload(String roleCode, int workloadDays) {
    }
}
