package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.project.domain.model.ProjectMemberRole;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** 版本计划中的一个工作项。工作量按工作日乘以投入人数自动估算。 */
public record InitiativePlanItem(
        String name,
        String roleCode,
        LocalDate startDate,
        LocalDate endDate,
        int headcount) {

    public InitiativePlanItem {
        name = name == null ? null : name.trim();
        roleCode = roleCode == null ? null : roleCode.trim().toUpperCase();
        if (name == null || name.isBlank()) {
            throw new DomainException("计划项名称不能为空");
        }
        if (name.length() > 160) {
            throw new DomainException("计划项名称不能超过 160 个字符");
        }
        if (roleCode == null || roleCode.isBlank()) {
            throw new DomainException("计划项责任角色不能为空");
        }
        try {
            ProjectMemberRole.valueOf(roleCode);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("未知的计划责任角色：" + roleCode);
        }
        if (startDate == null || endDate == null) {
            throw new DomainException("计划项开始和结束日期不能为空");
        }
        if (endDate.isBefore(startDate)) {
            throw new DomainException("计划项结束日期不能早于开始日期");
        }
        if (headcount < 1 || headcount > 99) {
            throw new DomainException("计划项投入人数必须在 1 到 99 之间");
        }
    }

    public int workingDays() {
        int days = 0;
        for (LocalDate cursor = startDate;
             !cursor.isAfter(endDate);
             cursor = cursor.plusDays(1)) {
            if (cursor.getDayOfWeek() != DayOfWeek.SATURDAY
                    && cursor.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days++;
            }
        }
        return days;
    }

    public int workloadDays() {
        return workingDays() * headcount;
    }
}
