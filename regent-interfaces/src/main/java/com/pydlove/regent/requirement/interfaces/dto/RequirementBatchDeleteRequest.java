package com.pydlove.regent.requirement.interfaces.dto;

import java.util.List;

/** 批量移入回收站的需求 ID。 */
public record RequirementBatchDeleteRequest(List<Long> ids) {
}
