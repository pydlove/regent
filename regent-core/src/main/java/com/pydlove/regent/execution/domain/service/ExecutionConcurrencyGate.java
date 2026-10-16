package com.pydlove.regent.execution.domain.service;

import com.pydlove.regent.execution.domain.model.ExecutionSettings;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 平台实际运行任务的动态闸门。
 *
 * <p>任务在真正开始执行前先取得许可，收场、挂起等人或失联回收时释放。上限调低不会打断已经
 * 取得许可的任务，只会在活跃数降到新上限以下之前拒绝新任务。
 */
public final class ExecutionConcurrencyGate {

    private final AtomicInteger activeCount = new AtomicInteger();
    private volatile int limit;

    public ExecutionConcurrencyGate(int initialLimit) {
        this.limit = new ExecutionSettings(initialLimit).maxConcurrentTasks();
    }

    public Optional<Permit> tryAcquire() {
        while (true) {
            int current = activeCount.get();
            if (current >= limit) {
                return Optional.empty();
            }
            if (activeCount.compareAndSet(current, current + 1)) {
                return Optional.of(new Permit(this));
            }
        }
    }

    /**
     * 把平台重启前已经存在的外部租约恢复为活跃占用。
     *
     * <p>这条路径刻意不检查上限：历史配置比当前值大时，活跃数本来就可能高于新上限；
     * 恢复事实之后，新任务会一直等到活跃数降到上限以下。
     */
    public Permit restoreActive() {
        activeCount.incrementAndGet();
        return new Permit(this);
    }

    public boolean hasAvailableSlot() {
        return activeCount.get() < limit;
    }

    public int activeCount() {
        return activeCount.get();
    }

    public int limit() {
        return limit;
    }

    public void updateLimit(int newLimit) {
        this.limit = new ExecutionSettings(newLimit).maxConcurrentTasks();
    }

    private void release(Permit permit) {
        if (permit.markReleased()) {
            activeCount.decrementAndGet();
        }
    }

    /** 一次并发占用的幂等许可。重复释放不会把活跃计数减成负数。 */
    public static final class Permit {

        private final ExecutionConcurrencyGate owner;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(ExecutionConcurrencyGate owner) {
            this.owner = owner;
        }

        public void release() {
            owner.release(this);
        }

        private boolean markReleased() {
            return released.compareAndSet(false, true);
        }
    }
}
