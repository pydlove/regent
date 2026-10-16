package com.pydlove.regent.execution.domain.repository;

import java.time.LocalDate;

/** 某个自然日收场的任务数，按调用方所在时区切天。 */
public record DailyCompletionCount(LocalDate day, long done, long failed) {
}
