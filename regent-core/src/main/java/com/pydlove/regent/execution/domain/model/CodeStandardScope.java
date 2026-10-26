package com.pydlove.regent.execution.domain.model;

/** 开发规范适用范围，仅用于组织和筛选，不参与执行优先级判断。 */
public enum CodeStandardScope {
    GENERAL,
    FRONTEND,
    BACKEND,
    TESTING,
    SECURITY,
    DATABASE,
    DEPLOYMENT
}
