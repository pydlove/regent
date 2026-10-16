package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 执行记录仓储 —— 「取聚合来改」的那一侧。
 *
 * <p>列表页与详情页要的行数很少（一个任务最多几十次尝试），因此不另开读模型：
 * 直接用聚合渲染 DTO 就够了。等到要按时间范围统计 token 消耗时再谈查询仓储。
 */
public interface TaskRunRepository {

    Optional<TaskRun> findById(TaskRunId id);

    /** 最近一次尝试。调度器靠它判断「任务是不是停在待续跑上」。 */
    Optional<TaskRun> findLatestByTask(TaskId taskId);

    List<TaskRun> listByTask(TaskId taskId);

    /**
     * 这个项目上有没有外部执行者正拿着的活跃租约。
     *
     * <p>判据是「库里还有没有没过期的租约」，而不是进程内的名单：平台重启、执行者换台机器，
     * 名单就没了，而租约还在。工作区独占靠它把关（同平台级并发闸门默认 1 的理由）。
     */
    boolean hasActiveLease(ProjectId projectId, Instant now);

    /** 租约已过期、却还停在 RUNNING 上的执行记录 —— 执行者失联了，等回收任务收场。 */
    List<TaskRun> findExpiredLeases(Instant now);

    /** 尚未过期、仍由外部执行者持有的租约。平台重启时用它恢复全局并发占用。 */
    List<TaskRun> findActiveLeases(Instant now);

    /**
     * 续租：只把租约到期时间往后推。
     *
     * <p>刻意不走 {@link #save} —— 心跳是高频写入，而 {@code save} 写的是整行；
     * 更要紧的是它会把内存里那份已经读旧的记录整行覆盖回去：回收任务刚把这条记录判了失联，
     * 一次迟到的续租就能把它复活成 RUNNING。
     */
    void renewLease(TaskRunId runId, Instant leaseExpiresAt);

    /** 新增或整体更新。新增时由实现回填数据库生成的主键（{@code TaskRun.assignIdentity}）。 */
    void save(TaskRun run);
}
