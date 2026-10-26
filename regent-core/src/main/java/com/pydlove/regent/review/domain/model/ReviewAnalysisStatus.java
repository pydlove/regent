package com.pydlove.regent.review.domain.model;

/** AI 或规则预审的执行状态；失败不改变代码事实是否可用。 */
public enum ReviewAnalysisStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    SKIPPED
}
