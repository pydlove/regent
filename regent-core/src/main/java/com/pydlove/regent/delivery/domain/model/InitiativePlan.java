package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 版本计划聚合：项目负责人维护排期、工作项和自动汇总的人天估算。 */
public record InitiativePlan(
        InitiativeId initiativeId,
        LocalDate planStartDate,
        LocalDate planEndDate,
        String milestones,
        List<InitiativePlanItem> items,
        UserId updatedBy,
        Instant updatedAt) {

    public InitiativePlan {
        if (initiativeId == null) {
            throw new DomainException("版本计划缺少立项");
        }
        if (planStartDate == null || planEndDate == null) {
            throw new DomainException("版本计划开始和结束日期不能为空");
        }
        if (planEndDate.isBefore(planStartDate)) {
            throw new DomainException("版本计划结束日期不能早于开始日期");
        }
        milestones = milestones == null || milestones.isBlank()
                ? null
                : milestones.trim();
        items = items == null ? List.of() : List.copyOf(items);
        if (items.isEmpty()) {
            throw new DomainException("版本计划至少需要一个工作项");
        }
        if (items.size() > 100) {
            throw new DomainException("版本计划不能超过 100 个工作项");
        }
        for (InitiativePlanItem item : items) {
            if (item.startDate().isBefore(planStartDate)
                    || item.endDate().isAfter(planEndDate)) {
                throw new DomainException("工作项「" + item.name() + "」必须在版本计划周期内");
            }
        }
        if (updatedBy == null || updatedAt == null) {
            throw new DomainException("版本计划更新信息不完整");
        }
        updatedAt = updatedAt.truncatedTo(ChronoUnit.MILLIS);
    }

    public int totalWorkloadDays() {
        return items.stream().mapToInt(InitiativePlanItem::workloadDays).sum();
    }

    public static InitiativePlan create(
            InitiativeId initiativeId,
            LocalDate planStartDate,
            LocalDate planEndDate,
            String milestones,
            List<InitiativePlanItem> items,
            UserId updatedBy) {
        return new InitiativePlan(
                initiativeId, planStartDate, planEndDate, milestones,
                items, updatedBy, Instant.now());
    }
}
