package com.pydlove.regent.requirement.interfaces.dto;

import java.util.List;

/** 回收站中要永久删除的需求 ID。 */
public record RequirementPurgeRequest(List<Long> ids) {
}
