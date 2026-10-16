package com.pydlove.regent.execution.interfaces.scheduler;

import com.pydlove.regent.execution.application.command.TaskDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 派发扫描 —— 平台「自己动起来」的那个心跳。
 *
 * <p>没有它，任务只能靠人点按钮；有了它，批准过决策的任务会被自动续跑，录入的需求也会
 * 在下一拍被拉起。默认开启，配置 {@code regent.execution.dispatch.enabled=false} 可关掉，
 * 测试环境就是关着的 —— 集成测试不该在自己跑的时候突然冒出几个真实执行进程。
 *
 * <p>心跳失败不抛出去：{@code @Scheduled} 的异常只会进日志，抛出来除了让当前这一拍停下
 * 没有任何作用，而下一拍还会照常来。真正要紧的是别让异常把线程池里的调度线程弄死。
 */
@Component
@ConditionalOnProperty(name = "regent.execution.dispatch.enabled", havingValue = "true", matchIfMissing = true)
public class TaskDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatchJob.class);

    private final TaskDispatchService taskDispatchService;
    private final int batchSize;

    public TaskDispatchJob(TaskDispatchService taskDispatchService,
                           @org.springframework.beans.factory.annotation.Value("${regent.execution.dispatch.batch-size:3}")
                           int batchSize) {
        this.taskDispatchService = taskDispatchService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${regent.execution.dispatch.interval-ms:15000}",
            initialDelayString = "${regent.execution.dispatch.initial-delay-ms:20000}")
    public void scan() {
        try {
            int claimed = taskDispatchService.dispatchCandidates(batchSize);
            if (claimed > 0) {
                log.info("派发扫描认领了 {} 个任务", claimed);
            }
        } catch (RuntimeException e) {
            log.warn("派发扫描失败（下一拍会重试）：{}", e.getMessage());
        }
    }
}
