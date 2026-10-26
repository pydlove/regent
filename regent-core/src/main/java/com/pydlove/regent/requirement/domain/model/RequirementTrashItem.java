package com.pydlove.regent.requirement.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;

import java.time.Instant;

/** 回收站中的需求快照，保留删除时间与操作人。 */
public record RequirementTrashItem(
        Requirement requirement,
        Instant deletedAt,
        UserId deletedBy) {
}
