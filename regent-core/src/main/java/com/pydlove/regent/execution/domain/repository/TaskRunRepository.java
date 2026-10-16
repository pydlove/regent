package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;

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

    /** 新增或整体更新。新增时由实现回填数据库生成的主键（{@code TaskRun.assignIdentity}）。 */
    void save(TaskRun run);
}
