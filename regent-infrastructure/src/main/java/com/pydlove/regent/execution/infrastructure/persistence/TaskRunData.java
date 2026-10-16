package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code task_run} 表的数据对象。
 *
 * <p>{@code changedFiles} 在这一层是 JSON 数组的原文（String）：领域层看到的是
 * {@code List<String>}，两边在 {@link TaskRunDataMapper} 里换 —— 转换点只有一处，
 * 序列化格式就永远不会从领域层漏出去。
 */
@Data
public class TaskRunData {

    private Long id;
    private Long taskId;
    private Integer attemptNo;
    private String status;
    private Integer exitCode;
    private Long tokenInput;
    private Long tokenCachedInput;
    private Long tokenOutput;
    private String branchName;
    private String summary;
    private String rawResult;
    private Boolean schemaValid;
    private String changedFiles;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
