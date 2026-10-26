package com.pydlove.regent.governance.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 决策单写侧的 MyBatis 映射接口。SQL 全部在 {@code mapper/DecisionMapper.xml}。
 *
 * <p>与 {@link DecisionQueryMapper} 分成两个接口而不是合并：读侧要连表、要分页、要算命中规则名，
 * 写侧只碰 decision 表自己。混在一个接口里，读侧的查询条件会影响写侧的参数校验，
 * 而且「这个 SQL 服务于哪一侧」会变得要靠猜。
 */
public interface DecisionMapper {

    DecisionData selectById(@Param("id") long id);

    /** 任务最近一条已决议的决策 —— 断点续跑的指令来源于它。 */
    DecisionData selectLatestResolvedByTask(@Param("taskId") long taskId);

    List<DecisionData> selectPendingByTaskForUpdate(@Param("taskId") long taskId);

    int insert(DecisionData data);

    int update(DecisionData data);
}
