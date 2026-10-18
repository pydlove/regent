package com.pydlove.regent.testing.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** 任务测试用例绑定的联表行。 */
@Data
public class TestCaseBindingRow {

    private Long testCaseId;
    private String name;
    private String testType;
    private String commandText;
    private String scriptFilename;
    private String scriptContent;
    private String scriptHash;
    private Boolean enabled;
    private Boolean deleted;
    private LocalDateTime boundAt;
}
