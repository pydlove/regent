package com.pydlove.regent.delivery.infrastructure.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InitiativePlanMapper {

    @Select("""
            SELECT initiative_id, plan_start_date, plan_end_date, milestones,
                   updated_by, updated_at
            FROM initiative_plan
            WHERE initiative_id = #{initiativeId}
            """)
    InitiativePlanData selectPlan(long initiativeId);

    @Select("""
            SELECT id, initiative_id, name, role_code, start_date, end_date,
                   headcount, sort_order, created_at
            FROM initiative_plan_item
            WHERE initiative_id = #{initiativeId}
            ORDER BY sort_order, id
            """)
    List<InitiativePlanItemData> selectItems(long initiativeId);

    @Insert("""
            INSERT INTO initiative_plan
                (initiative_id, plan_start_date, plan_end_date, milestones,
                 updated_by, updated_at)
            VALUES
                (#{initiativeId}, #{planStartDate}, #{planEndDate}, #{milestones},
                 #{updatedBy}, #{updatedAt})
            ON DUPLICATE KEY UPDATE
                plan_start_date = VALUES(plan_start_date),
                plan_end_date = VALUES(plan_end_date),
                milestones = VALUES(milestones),
                updated_by = VALUES(updated_by),
                updated_at = VALUES(updated_at)
            """)
    int upsertPlan(InitiativePlanData plan);

    @Delete("DELETE FROM initiative_plan_item WHERE initiative_id = #{initiativeId}")
    int deleteItems(long initiativeId);

    @Insert("""
            INSERT INTO initiative_plan_item
                (initiative_id, name, role_code, start_date, end_date,
                 headcount, sort_order, created_at)
            VALUES
                (#{initiativeId}, #{name}, #{roleCode}, #{startDate}, #{endDate},
                 #{headcount}, #{sortOrder}, #{createdAt})
            """)
    int insertItem(InitiativePlanItemData item);
}
