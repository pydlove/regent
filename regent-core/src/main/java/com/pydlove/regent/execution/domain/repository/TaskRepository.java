package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;

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

    /**
     * 在事务内锁住任务行再判断状态。
     *
     * <p>跨聚合编排（例如改插件绑定）不能只相信事务外的状态快照：调度器可能在这两步之间
     * 把任务改成 RUNNING。锁住同一行后，绑定与派发只能有一个先完成，后到者读到确定的新状态。
     */
    Optional<Task> findByIdForUpdate(TaskId id);

    /** 新增或整体更新。新增时由实现回填数据库生成的主键（{@code Task.assignIdentity}）。 */
    void save(Task task);

    /**
     * 只更新标题、指令与优先级。
     *
     * <p>实现必须在 SQL 条件里再次排除 {@code DONE}：领域守卫通过后，任务仍可能被并发完成，
     * 不能因为一次内容更新把已完成状态改回编辑时的旧状态。
     */
    void updateEditable(Task task);

    /**
     * 逻辑删除待执行、排队中或已终止的任务。
     *
     * <p>实现必须在 SQL 条件里再次约束
     * {@code deleted = 0 AND status IN ('PENDING', 'QUEUED', 'CANCELLED')}：
     * 应用层完成守卫后，任务仍可能被调度器并发派发，不能把已经起跑的任务误删。
     */
    void delete(TaskId id);

    /**
     * 可派发的候选任务，按优先级与 ID 排序。
     *
     * <p>两个来源合成一次查询：
     * <ol>
     *   <li>待执行（PENDING）或已提交排队（QUEUED）的任务 —— 项目必须处于启用状态；</li>
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

    /**
     * 用户明确提交等待执行位的任务，按优先级与创建顺序排列。
     *
     * <p>这条查询与 {@link #findDispatchable(int)} 分开：任务收场后立即补队时，
     * 只能拉 QUEUED，不能绕过 {@code dispatch.enabled} 把 PENDING 也顺手启动。
     */
    List<Task> findQueued(int limit);

    /**
     * 同上，但只看某个项目下的候选。
     *
     * <p>给外部执行者用：用户把「地址 + 项目」交给它，它就只该在这一个项目里找活。
     * 不把两者合成一个 {@code projectId} 可空的方法，是因为「不限项目」在调度器那里
     * 意味着「谁都可以领」，而在这里意味着「用户没指定」—— 同一个空值有两种含义时，
     * 迟早有人把其中一种传成另一种。
     */
    List<Task> findDispatchableInProject(ProjectId projectId, int limit);
}
