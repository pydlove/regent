package com.pydlove.regent.execution.interfaces.scheduler;

import com.pydlove.regent.execution.application.command.ExecutionRecoveryService;
import com.pydlove.regent.execution.application.command.ExecutionRecoveryService.ExecutionRecoveryResult;
import com.pydlove.regent.execution.application.command.TaskDispatchService;
import com.pydlove.regent.testing.application.command.TestCaseExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 应用就绪后的第一件事：把上次进程留下的内部执行收场，再立刻派发。
 *
 * <p>不能只依赖定时扫描。普通 {@code RUNNING} 任务不在派发候选里，必须先由恢复服务
 * 放回 {@code QUEUED}；而人已经批准、只差续跑的任务也需要这第一拍及时接手。
 *
 * <p>恢复和派发分开兜底：恢复失败仍然尝试派发已经合法的续跑任务；
 * 派发失败则留给后续定时扫描重试。
 */
@Component
@ConditionalOnProperty(name = "regent.execution.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class ExecutionRecoveryRunner implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRecoveryRunner.class);

    private final ExecutionRecoveryService recoveryService;
    private final TaskDispatchService taskDispatchService;
    private final TestCaseExecutionService testCaseExecutionService;
    private final int batchSize;
    private final boolean dispatchEnabled;

    public ExecutionRecoveryRunner(ExecutionRecoveryService recoveryService,
                                   TaskDispatchService taskDispatchService,
                                   TestCaseExecutionService testCaseExecutionService,
                                   @Value("${regent.execution.dispatch.batch-size:3}") int batchSize,
                                   @Value("${regent.execution.dispatch.enabled:true}") boolean dispatchEnabled) {
        this.recoveryService = recoveryService;
        this.taskDispatchService = taskDispatchService;
        this.testCaseExecutionService = testCaseExecutionService;
        this.batchSize = batchSize;
        this.dispatchEnabled = dispatchEnabled;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        recover();
        recoverTests();
        dispatch();
    }

    private void recoverTests() {
        try {
            int interrupted = testCaseExecutionService.recoverInterruptedRuns();
            if (interrupted > 0) {
                log.info("启动恢复完成：{} 条测试执行记录标记为已中断", interrupted);
            }
        } catch (RuntimeException e) {
            log.warn("启动测试恢复失败：{}", e.getMessage());
        }
    }

    private void recover() {
        try {
            ExecutionRecoveryResult result = recoveryService.recoverInterruptedExecutions();
            if (result.hasChanges()) {
                log.info("启动恢复完成：{} 条执行记录标记为已中断，{} 个任务重新排队",
                        result.interruptedRuns(), result.requeuedTasks());
            }
            for (ExecutionRecoveryService.Failure failure : result.failures()) {
                log.warn("任务 {} 启动恢复失败：{}", failure.taskId(), failure.message());
            }
        } catch (RuntimeException e) {
            log.warn("启动执行恢复失败（定时派发仍会继续工作）：{}", e.getMessage());
        }
    }

    private void dispatch() {
        if (!dispatchEnabled) {
            return;
        }
        try {
            int claimed = taskDispatchService.dispatchCandidates(batchSize);
            if (claimed > 0) {
                log.info("启动后立即派发了 {} 个任务", claimed);
            }
        } catch (RuntimeException e) {
            log.warn("启动后的立即派发失败（下一拍会重试）：{}", e.getMessage());
        }
    }
}
