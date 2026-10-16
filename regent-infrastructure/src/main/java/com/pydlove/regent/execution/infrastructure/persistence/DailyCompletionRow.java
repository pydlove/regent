package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDate;

/** 按天分组的收场统计行：{@code day} 已按调用方时区切好。 */
@Data
public class DailyCompletionRow {

    private LocalDate day;
    private long done;
    private long failed;
}
