package com.pydlove.regent.governance.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 决策列表的数据行 —— {@code decision} 表连上任务标题、项目名与命中规则名之后的形状。
 *
 * <p>连表在 SQL 里做而不是在 Java 里拼：列表每页只有二十行，一次 join 比「查完决策再逐个
 * 查任务」少二十次往返，也避免调用方忘了补名字。代价是读模型与表结构不再一一对应，
 * 所以它叫 Row 而不是 Data。
 */
@Data
public class DecisionRow {

    private Long id;
    private Long taskId;
    private String taskTitle;
    private Long projectId;
    private String projectName;
    private String aiLevel;
    private String effectiveLevel;
    private String matchedRuleName;
    private String status;
    private String question;
    private String options;
    private String recommendation;
    private Boolean reversible;
    private String blastRadius;
    private String resolvedBy;
    private String resolvedChoice;
    private String resolutionNote;
    private LocalDateTime raisedAt;
    private LocalDateTime resolvedAt;
}
