package com.pydlove.regent.bug.interfaces.dto;

/** 判定缺陷不修复时提交的理由，可以为空。 */
public record BugWontFixRequest(String reason) {
}
