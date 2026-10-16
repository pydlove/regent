package com.pydlove.regent.governance.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 决策单整行的数据对象，与 {@code decision} 表一一对应。
 *
 * <p>与读模型 {@link DecisionRow} 分开：Row 连了任务标题与项目名，用于渲染列表；
 * 本类只承载聚合真正拥有的那些列。写回时若拿 Row 当载体，UPDATE 语句就会带上
 * 一堆不属于这张表的字段。
 */
@Data
public class DecisionData {

    private Long id;
    private Long taskId;
    private Long projectId;
    private String aiLevel;
    private String effectiveLevel;
    private Long matchedRuleId;
    private String status;
    private String question;
    /** JSON 数组的原文。领域层看到的是 List&lt;String&gt;，两边在 {@link DecisionDataMapper} 里换。 */
    private String options;
    private String recommendation;
    private Boolean reversible;
    private String blastRadius;
    private String resolvedBy;
    private String resolvedChoice;
    private String resolutionNote;
    private LocalDateTime raisedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
