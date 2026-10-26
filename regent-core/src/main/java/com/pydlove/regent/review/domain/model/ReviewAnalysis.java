package com.pydlove.regent.review.domain.model;

import java.util.List;

/** 预审输出。没有代码位置的核心阻断问题会在聚合创建时被拒绝。 */
public record ReviewAnalysis(
        List<ReviewChangeUnit> changeUnits,
        List<ReviewFinding> findings,
        List<ReviewEvidence> evidence) {

    public ReviewAnalysis {
        changeUnits = changeUnits == null ? List.of() : List.copyOf(changeUnits);
        findings = findings == null ? List.of() : List.copyOf(findings);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static ReviewAnalysis empty() {
        return new ReviewAnalysis(List.of(), List.of(), List.of());
    }
}
