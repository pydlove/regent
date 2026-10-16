package com.pydlove.regent.execution.application.query;

import java.time.Instant;

/**
 * 可领任务清单里的一行 —— 「这个项目上现在有哪些活」。
 *
 * <p>外部执行者先看清单再决定领哪一条，比盲领更接近人的用法：人可以指定「干这条」，
 * 也可以说「你自己挑」。清单只列<b>候选</b>（待执行/排队中、或批准后等续跑），
 * 已经在别人手上的任务不在里面 —— 它此刻不可领，列出来只会让人误以为可以拿。
 *
 * @param claimable 现在能不能领：项目工作区被别人占着时为 false
 * @param reason    {@code claimable=false} 时说明为什么，其余时候为空字符串
 */
public record AgentTaskSummary(
        long taskId,
        String projectCode,
        String title,
        String status,
        int priority,
        String source,
        Instant createdAt,
        boolean claimable,
        String reason) {
}
