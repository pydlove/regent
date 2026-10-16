package com.pydlove.regent.execution.application.command;

import java.time.Instant;

/**
 * 一次心跳的回复 —— 「你还拿着活，现在该干什么」。
 *
 * <p>它的价值全在 {@link #nextAction}：外部执行者是个循环，它必须能靠这一句话决定
 * 「继续干 / 等批复 / 重新认领 / 收工」，而不是靠猜平台的状态。
 *
 * @param runId          执行记录 ID
 * @param taskId         任务 ID
 * @param runStatus      这次尝试的状态：RUNNING / SUCCESS / FAILED / BLOCKED / TIMEOUT
 * @param taskStatus     任务的状态：PENDING / QUEUED / RUNNING / BLOCKED / DONE / FAILED / CANCELLED
 * @param nextAction     下一步该干什么（见 {@link NextAction}）
 * @param note           一句人话，解释为什么是这一步；界面与日志都用它
 * @param decisionId     相关决策单 ID，没有则为 null
 * @param decisionStatus 决策单状态：PENDING / APPROVED / REJECTED / AUTO_RESOLVED
 * @param leaseSeconds   续租后的租约时长
 * @param leaseExpiresAt 续租后的到期时间；执行记录已收场时为 null（不再要求心跳）
 */
public record AgentRunSnapshot(
        long runId,
        long taskId,
        String runStatus,
        String taskStatus,
        NextAction nextAction,
        String note,
        Long decisionId,
        String decisionStatus,
        int leaseSeconds,
        Instant leaseExpiresAt) {

    /** 心跳回复里唯一需要执行者照着做的字段。 */
    public enum NextAction {
        /** 接着干，活还是你的。 */
        KEEP_WORKING,
        /** 平台把你的问题交给人了，停手等着；批完再回来认领。 */
        WAIT_DECISION,
        /** 可以（重新）认领：决策已归位，或者任务回到了待执行。 */
        CLAIM_AGAIN,
        /** 收工：任务已经终态或被人工终止，别再动了。 */
        STOP
    }
}
