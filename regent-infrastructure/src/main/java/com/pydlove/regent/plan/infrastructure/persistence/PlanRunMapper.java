package com.pydlove.regent.plan.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** {@code plan_run} 与 {@code plan_run_item} 的 MyBatis 映射。 */
public interface PlanRunMapper {

    PlanRunData selectById(@Param("id") long id);

    PlanRunData selectLatestByPlan(@Param("planId") long planId);

    List<PlanRunData> selectByPlan(@Param("planId") long planId);

    List<PlanRunData> selectRunning(@Param("limit") int limit);

    List<PlanRunItemData> selectItems(@Param("planRunId") long planRunId);

    int insert(PlanRunData data);

    int update(PlanRunData data);

    int insertItem(PlanRunItemData data);

    int updateItem(PlanRunItemData data);
}
