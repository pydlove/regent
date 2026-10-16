package com.pydlove.regent.project.application.command;

import com.pydlove.regent.shared.kernel.DecisionLevel;

/**
 * 注册项目的入参。
 *
 * @param code                 项目唯一标识，如 {@code app_audit}
 * @param name                 展示名称
 * @param path                 工作区绝对路径，codex exec -C 用
 * @param techStack            技术栈描述，可空
 * @param verifyMethod         验证命令，可空
 * @param defaultDecisionLevel 项目级自动决策阈值；生效等级高于它就必须由人拍板
 */
public record RegisterProjectCommand(
        String code,
        String name,
        String path,
        String techStack,
        String verifyMethod,
        DecisionLevel defaultDecisionLevel) {
}
