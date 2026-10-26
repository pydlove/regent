package com.pydlove.regent.delivery.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InitiativePlanTest {

    @Test
    @DisplayName("版本计划按工作日乘投入人数自动汇总人天")
    void calculatesWorkloadByWorkingDaysAndHeadcount() {
        InitiativePlanItem item = new InitiativePlanItem(
                "研发实现",
                "DEVELOPER",
                LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 30),
                2);

        assertEquals(5, item.workingDays());
        assertEquals(10, item.workloadDays());

        InitiativePlan plan = InitiativePlan.create(
                InitiativeId.of(7L),
                LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 30),
                "周五完成联调",
                List.of(item),
                UserId.of(1L));
        assertEquals(10, plan.totalWorkloadDays());
    }

    @Test
    @DisplayName("计划项必须位于版本计划周期内")
    void rejectsItemOutsidePlanRange() {
        assertThrows(DomainException.class, () -> InitiativePlan.create(
                InitiativeId.of(7L),
                LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 30),
                null,
                List.of(new InitiativePlanItem(
                        "越界工作",
                        "DEVELOPER",
                        LocalDate.of(2026, 10, 25),
                        LocalDate.of(2026, 10, 28),
                        1)),
                UserId.of(1L)));
    }

    @Test
    @DisplayName("版本计划至少包含一个工作项")
    void requiresAtLeastOneItem() {
        assertThrows(DomainException.class, () -> InitiativePlan.create(
                InitiativeId.of(7L),
                LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 10, 30),
                null,
                List.of(),
                UserId.of(1L)));
    }
}
