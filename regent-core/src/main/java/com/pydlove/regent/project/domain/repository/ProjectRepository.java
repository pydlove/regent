package com.pydlove.regent.project.domain.repository;

import com.pydlove.regent.project.domain.model.Project;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.identity.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * 项目聚合的写侧仓储。接口在领域层，MyBatis 实现在基础设施层。
 *
 * <p>普通读画像仍走 {@link ProjectProfileRepository}；写侧只为项目资料生成状态提供
 * 按 ID 恢复与更新，避免把状态机的持久化责任放到查询投影上。
 */
public interface ProjectRepository {

    /**
     * 项目标识是否已被占用。
     *
     * <p>用它先查一次，是为了把「标识重复」变成一句人能看懂的话；直接插入撞唯一键
     * 只会得到一串数据库报错。数据库的唯一键仍然是最终防线，查与插之间的竞态由它兜底。
     */
    boolean existsByCode(String code);

    /** 新增项目，insert 后由实现回填主键（{@code Project.assignIdentity}）。 */
    void save(Project project);

    Optional<Project> findById(ProjectId id);

    /** 只在指定账号名下取项目；接口侧改项目前必须先过这一层。 */
    default Optional<Project> findByIdOwned(ProjectId id, UserId ownerId) {
        return findById(id);
    }

    List<Project> findBySummaryStatus(ProjectSummaryStatus status);

    /** 持久化项目资料生成状态相关字段。 */
    void update(Project project);

    /** 持久化名称、路径、决策阈值与启停状态；不触碰项目资料字段。 */
    void updateDetails(Project project);
}
