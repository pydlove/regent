package com.pydlove.regent.project.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code project} 表的数据对象，字段与列一一对应。
 *
 * <p>只包含写侧用得到的列：{@code deleted} 由 SQL 写死为 0（现在没有删除项目的用例），
 * 不进领域对象 —— 逻辑删除是实现细节，领域层不该知道「记录还在库里但被标记为删除」。
 */
@Data
public class ProjectData {

    private Long id;
    private Long organizationId;
    private Long ownerId;
    private String code;
    private String name;
    private String path;
    private String techStack;
    private String verifyMethod;
    private String defaultDecisionLevel;
    private String versionSource;
    private String fallbackVersion;
    private Boolean enabled;
    private String summaryStatus;
    private String summary;
    private String summaryError;
    private LocalDateTime summaryGeneratedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
