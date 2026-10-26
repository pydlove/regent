package com.pydlove.regent.review.application.command;

import com.pydlove.regent.review.domain.model.ReviewOperatorId;
import com.pydlove.regent.review.domain.model.ReviewTaskId;

/** 使用最终提交说明，把当前审阅快照覆盖的文件提交到本地 Git。 */
public record ReviewGitCommitCommand(
        ReviewTaskId taskId,
        ReviewOperatorId operatorId,
        String message) {
}
