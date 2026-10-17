package com.pydlove.regent.plan.interfaces.scheduler;

import com.pydlove.regent.plan.application.command.PlanExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 计划定时扫描：启动到点计划，并刷新运行中计划的逐项结果。 */
@Component
@ConditionalOnProperty(
        name = "regent.plan.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PlanSchedulerJob {

    private static final Logger log = LoggerFactory.getLogger(PlanSchedulerJob.class);

    private final PlanExecutionService planExecutionService;
    private final int batchSize;

    public PlanSchedulerJob(
            PlanExecutionService planExecutionService,
            @Value("${regent.plan.scheduler.batch-size:10}") int batchSize) {
        this.planExecutionService = planExecutionService;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${regent.plan.scheduler.interval-ms:15000}",
            initialDelayString = "${regent.plan.scheduler.initial-delay-ms:20000}")
    public void scan() {
        try {
            int started = planExecutionService.startDuePlans(batchSize);
            int settled = planExecutionService.refreshRunningRuns(batchSize);
            if (started > 0 || settled > 0) {
                log.info("计划扫描启动 {} 个，完成 {} 个", started, settled);
            }
        } catch (RuntimeException exception) {
            log.warn("计划扫描失败（下一拍会重试）：{}", exception.getMessage());
        }
    }
}
