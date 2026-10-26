package com.pydlove.regent.project.interfaces.dto;

/**
 * 注册项目的请求体。
 *
 * <p>{@code defaultDecisionLevel} 用字符串接收而不是枚举：{@code "L1"} 与 {@code "1"} 都该收下，
 * 因为这层宽松解析是 {@code DecisionLevel.parse} 的既有约定；非法值会变成 400 而不是 500。
 *
 * <p>它是必填的。阈值决定「谁有权替人拍板」，这种字段不该由接口层悄悄补一个默认值 ——
 * 数据库列上的 DEFAULT 'L1' 是兜底，不是给正常请求走的路径。
 */
public record ProjectCreateRequest(
        Long organizationId,
        String code,
        String name,
        String path,
        String defaultDecisionLevel,
        String versionSource,
        String fallbackVersion) {

    public ProjectCreateRequest(String code, String name, String path, String defaultDecisionLevel) {
        this(null, code, name, path, defaultDecisionLevel, "GIT_TAG", null);
    }
}
