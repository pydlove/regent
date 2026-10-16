package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务列表行的数据对象：{@code task} 左连 {@code project}，再补上等级、token 与执行时长派生列。
 *
 * <p>{@code level}、{@code tokens} 与 {@code executionMillis} 是子查询算出来的，允许为 null
 * （任务还没产生决策或执行记录），到读模型里再落成 L0 与 0。
 */
@Data
public class TaskListRow {

    private Long id;
    private Long projectId;
    private String projectName;
    private String source;
    private String title;
    private String status;
    private Integer priority;
    private String level;
    private String branchName;
    private Long blockingDecisionId;
    private Integer attemptCount;
    private Long tokens;
    private Long executionMillis;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
