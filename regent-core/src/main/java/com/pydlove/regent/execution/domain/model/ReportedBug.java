package com.pydlove.regent.execution.domain.model;

/**
 * 执行器在一轮执行中发现的缺陷。
 *
 * <p>这里保留字符串而不是直接依赖 bug 上下文的值对象，是为了让「codex 输出解析」和
 * 「领域落库」保持边界清晰：格式翻译停在执行结果，缺陷聚合再做最终校验。
 */
public record ReportedBug(
        String title,
        String severity,
        String reproduction,
        String filePath,
        String detail) {
}
