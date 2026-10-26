package com.pydlove.regent.delivery.interfaces.dto;

import com.pydlove.regent.delivery.application.command.SaveInitiativePlanCommand;

import java.time.LocalDate;
import java.util.List;

public record InitiativePlanRequest(
        LocalDate planStartDate,
        LocalDate planEndDate,
        String milestones,
        List<Item> items) {

    public SaveInitiativePlanCommand toCommand() {
        return new SaveInitiativePlanCommand(
                planStartDate,
                planEndDate,
                milestones,
                items == null
                        ? List.of()
                        : items.stream()
                                .map(item -> new SaveInitiativePlanCommand.Item(
                                        item.name(),
                                        item.roleCode(),
                                        item.startDate(),
                                        item.endDate(),
                                        item.headcount() == null ? 0 : item.headcount()))
                                .toList());
    }

    public record Item(
            String name,
            String roleCode,
            LocalDate startDate,
            LocalDate endDate,
            Integer headcount) {
    }
}
