package com.pydlove.regent.delivery.application.command;

import java.time.LocalDate;
import java.util.List;

/** 项目负责人保存版本计划。 */
public record SaveInitiativePlanCommand(
        LocalDate planStartDate,
        LocalDate planEndDate,
        String milestones,
        List<Item> items) {

    public SaveInitiativePlanCommand {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(
            String name,
            String roleCode,
            LocalDate startDate,
            LocalDate endDate,
            int headcount) {
    }
}
