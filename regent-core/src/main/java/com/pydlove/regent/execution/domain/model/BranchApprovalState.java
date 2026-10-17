package com.pydlove.regent.execution.domain.model;

/**
 * 任务首次创建分支时的审批状态。
 *
 * <p>平台默认一任务一分支，但分支本身必须经人批准后才能创建。这个状态只描述
 * 「本次分支创建申请走到哪一步」，不复用 {@link TaskStatus}：任务是否在执行、是否被阻塞
 * 与分支是否获批是两件事，把它们揉进同一个枚举会让状态机出现大量无意义分支。
 */
public enum BranchApprovalState {

    /** 没有待处理或已放行的分支创建申请。 */
    NONE,

    /** 已生成决策单，等待人工批准；此时绝不能碰 Git。 */
    PENDING,

    /** 人工已批准，下一次派发可以创建分支；创建成功后回到 {@link #NONE}。 */
    APPROVED
}
