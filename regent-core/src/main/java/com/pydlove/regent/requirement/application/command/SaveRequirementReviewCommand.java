package com.pydlove.regent.requirement.application.command;

import java.time.LocalDate;

/** 登记一次线下需求评审。 */
public record SaveRequirementReviewCommand(
        LocalDate reviewDate,
        String minutes) {
}
