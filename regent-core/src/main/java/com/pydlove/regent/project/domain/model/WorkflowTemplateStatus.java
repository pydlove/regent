package com.pydlove.regent.project.domain.model;

/** 全局研发流程模板版本的生命周期状态。 */
public enum WorkflowTemplateStatus {
    /** 当前可用于新项目选择的版本。 */
    PUBLISHED,
    /** 已被同模板的新版本替代，只用于历史项目与版本展示。 */
    SUPERSEDED,
    /** 已停用，不再用于新项目。 */
    ARCHIVED
}
