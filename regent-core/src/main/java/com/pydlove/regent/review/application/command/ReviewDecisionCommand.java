package com.pydlove.regent.review.application.command;

import com.pydlove.regent.review.domain.model.ReviewDecisionType;
import com.pydlove.regent.review.domain.model.ReviewOperatorId;
import com.pydlove.regent.review.domain.model.ReviewTaskId;

/** 人工验收命令。 */
public record ReviewDecisionCommand(
        ReviewTaskId taskId,
        ReviewDecisionType decision,
        ReviewOperatorId operatorId,
        String comment) {
}
