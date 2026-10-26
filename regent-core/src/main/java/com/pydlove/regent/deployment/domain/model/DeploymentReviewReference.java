package com.pydlove.regent.deployment.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 正式部署依据的审阅事实引用，只保存 ID，不持有审阅聚合。 */
public record DeploymentReviewReference(
        long taskId,
        long snapshotId,
        long decisionId) {

    public DeploymentReviewReference {
        if (taskId < 1 || snapshotId < 1 || decisionId < 1) {
            throw new DomainException("部署审阅引用 ID 必须为正数");
        }
    }
}
