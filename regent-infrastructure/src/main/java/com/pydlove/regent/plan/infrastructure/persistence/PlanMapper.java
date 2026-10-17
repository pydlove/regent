package com.pydlove.regent.plan.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** {@code plan} 与 {@code plan_task} 的 MyBatis 映射。 */
public interface PlanMapper {

    PlanData selectById(@Param("id") long id);

    PlanData selectByIdForUpdate(@Param("id") long id);

    List<PlanData> selectAll();

    List<PlanData> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    List<Long> selectTaskIds(@Param("planId") long planId);

    int insert(PlanData data);

    int insertTask(PlanTaskData data);

    int update(PlanData data);
}
