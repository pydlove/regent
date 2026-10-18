package com.pydlove.regent.testing.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code test_case} 表读侧数据对象。 */
@Data
public class TestCaseRow {

    private Long id;
    private Long projectId;
    private String name;
    private String testType;
    private String description;
    private String commandText;
    private String scriptFilename;
    private String scriptContent;
    private String scriptHash;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
