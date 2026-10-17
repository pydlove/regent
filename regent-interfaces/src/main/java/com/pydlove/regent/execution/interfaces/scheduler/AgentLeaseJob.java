package com.pydlove.regent.execution.interfaces.scheduler;

import com.pydlove.regent.execution.application.command.AgentRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 失联回收 —— 外部执行者的「你还在吗」。
 *
 * <p>外部执行者跑在平台看不见也杀不掉的进程里：用户把编辑器一关、笔记本一合、网一断，
 * 那条执行记录就会永远停在 RUNNING 上，任务跟着卡在「执行中」，工作台上没有任何线索。
 * 租约到期就是那句「它不在了」，这个心跳负责把结论落下来：这次尝试判失败、工作区放开、
 * 任务回到可以重试的状态。
 *
 * <p>它是幂等的：回收只对「还挂在 RUNNING 上、租约已过期」的记录生效，
 * 上一拍收场过的不会再被碰第二次。心跳失败只进日志 —— 抛出去除了让这一拍停下没有别的作用。
 */
@Component
@ConditionalOnProperty(name = "regent.agent.lease-sweep.enabled", havingValue = "true", matchIfMissing = true)
public class AgentLeaseJob {

    private static final Logger log = LoggerFactory.getLogger(AgentLeaseJob.class);

    private final AgentRunService agentRunService;

    public AgentLeaseJob(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @Scheduled(fixedDelayString = "${regent.agent.lease-sweep.interval-ms:60000}",
            initialDelayString = "${regent.agent.lease-sweep.initial-delay-ms:45000}")
    public void sweep() {
        try {
            int released = agentRunService.releaseExpiredLeases();
            if (released > 0) {
                log.warn("回收了 {} 条失联的外部执行记录（租约到期未续）", released);
            }
        } catch (RuntimeException e) {
            log.warn("失联回收失败（下一拍会重试）：{}", e.getMessage());
        }
    }
}
