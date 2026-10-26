package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.application.query.InitiativePlanView;
import com.pydlove.regent.delivery.domain.model.InitiativePlan;
import com.pydlove.regent.delivery.domain.model.InitiativePlanItem;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 版本计划与自动工作量估算。 */
public record InitiativePlanResponse(
        LocalDate planStartDate,
        LocalDate planEndDate,
        String milestones,
        int totalWorkloadDays,
        List<RoleWorkloadResponse> roleWorkloads,
        List<ItemResponse> items,
        long updatedBy,
        Instant updatedAt) {

    public static InitiativePlanResponse from(InitiativePlanView view) {
        if (view == null) return null;
        InitiativePlan plan = view.plan();
        return new InitiativePlanResponse(
                plan.planStartDate(),
                plan.planEndDate(),
                plan.milestones(),
                view.totalWorkloadDays(),
                view.roleWorkloads().stream()
                        .map(role -> new RoleWorkloadResponse(
                                role.roleCode(), role.workloadDays()))
                        .toList(),
                plan.items().stream().map(ItemResponse::from).toList(),
                plan.updatedBy().value(),
                plan.updatedAt());
    }

    public record ItemResponse(
            String name,
            String roleCode,
            LocalDate startDate,
            LocalDate endDate,
            int headcount,
            int workingDays,
            int workloadDays) {

        public static ItemResponse from(InitiativePlanItem item) {
            return new ItemResponse(
                    item.name(),
                    item.roleCode(),
                    item.startDate(),
                    item.endDate(),
                    item.headcount(),
                    item.workingDays(),
                    item.workloadDays());
        }
    }

    public record RoleWorkloadResponse(String roleCode, int workloadDays) {
    }
}
