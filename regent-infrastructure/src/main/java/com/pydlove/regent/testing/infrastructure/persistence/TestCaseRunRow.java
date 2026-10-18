package com.pydlove.regent.testing.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code test_case_run} 表读侧数据对象。 */
@Data
public class TestCaseRunRow {

    private Long id;
    private Long projectId;
    private Long taskId;
    private String triggerType;
    private Long testCaseId;
    private String testCaseName;
    private String testType;
    private String commandText;
    private String scriptFilename;
    private String scriptContent;
    private String scriptHash;
    private String status;
    private Integer exitCode;
    private String summary;
    private String output;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
