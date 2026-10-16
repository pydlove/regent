package com.pydlove.regent.execution.application.command;

/**
 * 一次上报的结算结果 —— 执行者据此决定收工还是接着干。
 *
 * @param outcome     {@link Outcome}：接着干 / 挂起等人 / 做完了 / 失败了
 * @param runStatus   这次尝试的最终状态
 * @param taskStatus  任务的状态
 * @param instruction 只有 {@link Outcome#CONTINUE} 时非空：接着要做什么（已含人的结论）
 * @param decisionId  相关决策单 ID；CONTINUE 与 BLOCKED 时非空
 * @param decisionLevel 生效等级（就高不就低之后），没有决策时为 null
 * @param note        一句人话，说明平台为什么这样判；界面与日志都用它
 * @param roundsLeft  本次尝试还能连续决策几轮；用完平台会判失败，避免自己跟自己聊到天亮
 */
public record AgentReportOutcome(
        Outcome outcome,
        String runStatus,
        String taskStatus,
        String instruction,
        Long decisionId,
        String decisionLevel,
        String note,
        int roundsLeft) {

    public enum Outcome {
        /** 平台替你把问题定了（阈值内且可逆），带着结论接着干。 */
        CONTINUE,
        /** 越过阈值或不可逆：挂起等人拍板，你的活到此为止。 */
        BLOCKED,
        /** 任务完成。 */
        DONE,
        /** 这一次没做成。 */
        FAILED
    }

    public boolean keepWorking() {
        return outcome == Outcome.CONTINUE;
    }
}
