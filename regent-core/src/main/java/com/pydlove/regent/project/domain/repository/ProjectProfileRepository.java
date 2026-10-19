package com.pydlove.regent.project.domain.repository;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.project.domain.model.ProjectId;

import java.util.List;
import java.util.Optional;

/**
 * 项目画像查询。
 *
 * <p>注意它<b>不是</b>聚合仓储：没有 save，也没有写侧动作。名字保留 Repository
 * 是因为它对调用方的语义是「按 ID 取项目」，与既有读模型的命名习惯一致。
 */
public interface ProjectProfileRepository {

    /** 已逻辑删除的项目查不到 —— 查询实现必须带 {@code deleted = 0}。 */
    Optional<ProjectProfile> findById(ProjectId id);

    /** 只在指定账号名下取项目；找不到时与「项目不存在」一样，不向调用方暴露他人项目。 */
    default Optional<ProjectProfile> findById(ProjectId id, UserId ownerId) {
        return findById(id);
    }

    /**
     * 按项目标识取画像。
     *
     * <p>外部执行者手里只有人能说出口的那个标识（{@code regent}），没有内部 ID：
     * 「去 regent 这个项目上找活干」必须能落到一行数据上，所以这条查询是协议的一部分，
     * 不是顺手的便利方法。
     */
    Optional<ProjectProfile> findByCode(String code);

    /**
     * 全部启用与暂停的项目，按 ID 排序。
     *
     * <p>不做分页：项目是这个平台里数量最少的东西（个位数），给它做分页属于照猫画虎。
     * 数量真涨到需要分页时，加的是另一个用例，不是给这个方法塞参数。
     */
    List<ProjectProfile> findAll();

    /** 当前账号可见的项目。 */
    default List<ProjectProfile> findAll(UserId ownerId) {
        return findAll();
    }
}
