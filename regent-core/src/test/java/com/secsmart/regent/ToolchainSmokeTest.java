package com.secsmart.regent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具链冒烟测试：确认 JUnit 5 被 surefire 实际执行。
 *
 * <p>存在的唯一理由：{@code api_parent} 未管理 surefire 版本，Maven 默认版本 2.12.4 无法执行
 * JUnit 5，测试会被<b>静默跳过</b>且构建仍然「成功」——这是最危险的失败模式。
 * 本项目已在聚合 POM 中钉死 surefire 3.5.4，本类用于验证该约束真的生效。
 *
 * <p>第一个真实单测落地后，本类即可删除。
 */
class ToolchainSmokeTest {

    @Test
    @DisplayName("JUnit 5 测试应被 surefire 实际执行")
    void junit5IsExecuted() {
        assertTrue(true);
    }
}
