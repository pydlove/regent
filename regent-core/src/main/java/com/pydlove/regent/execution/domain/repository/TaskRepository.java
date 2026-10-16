package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;

import java.util.List;
import java.util.Optional;

/**
 * 任务聚合仓储。接口定义在领域层，实现（MyBatis）在基础设施层 ——
 * 领域层因此完全不知道表名、SQL 和 MyBatis 的存在，单测可以直接实现本办法替代。
 *
 * <p>只负责「取聚合来改」这一件事。列表查询走 {@link TaskQueryRepository}：
 * 两者若合在一起，聚合的字段需求很快会被读模型的展示需求带偏。
 */
public interface TaskRepository {

    Optional<Task> findById(TaskId id);

    /** 新增或整体更新。新增时由实现回填数据库生成的主键（{@code Task.assignIdentity}）。 */
    void save(Task task);

    /**
     * 可派发的候选任务，按优先级与 ID 排序。
     *
     * <p>两个来源合成一次查询：
     * <ol>
     *   <li>待执行（PENDING）的任务 —— 项目必须处于启用状态；</li>
     *   <li>等待断点续跑的任务 —— 状态为 RUNNING，且最近一次执行记录停在 BLOCKED。
     *       决策批准后任务就长这样：人拍了板、会话还在、只是没人把它拉起来。
     *       用「最近一次执行记录」而不是新加一个状态列，是因为这个状态本来就完全
     *       由已有的两条事实决定，多存一份必然有一天与事实不一致。</li>
     * </ol>
     *
     * <p>不在这个集合里的 RUNNING 任务（例如进程崩了留下的中途状态）不会被自动拉起：
     * 自动挑「看起来该继续」的任务比漏跑一个危险得多，宁可让人在界面上重试。
     */
    List<Task> findDispatchable(int limit);
}
