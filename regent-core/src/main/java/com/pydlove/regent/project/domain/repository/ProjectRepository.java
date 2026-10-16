package com.pydlove.regent.project.domain.repository;

import com.pydlove.regent.project.domain.model.Project;

/**
 * 项目聚合的写侧仓储。接口在领域层，MyBatis 实现在基础设施层。
 *
 * <p>只有注册需要的两个动作。不提供 findById —— 读画像走
 * {@link ProjectProfileRepository}，那是执行链路的读模型；等真有「取项目来改」的用例时再加。
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
}
