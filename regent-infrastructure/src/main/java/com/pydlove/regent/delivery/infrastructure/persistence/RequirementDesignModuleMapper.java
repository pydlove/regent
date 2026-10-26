package com.pydlove.regent.delivery.infrastructure.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface RequirementDesignModuleMapper {

    @Select("""
            SELECT id, delivery_id, name, description, assignee_id, status,
                   design_summary, design_content, created_by, created_at, updated_at
            FROM requirement_design_module
            WHERE delivery_id = #{deliveryId}
            ORDER BY id
            """)
    List<RequirementDesignModuleData> selectByDelivery(long deliveryId);

    @Select("""
            SELECT id, delivery_id, name, description, assignee_id, status,
                   design_summary, design_content, created_by, created_at, updated_at
            FROM requirement_design_module
            WHERE id = #{id}
            """)
    RequirementDesignModuleData selectById(long id);

    @Select("""
            SELECT id, delivery_id, name, description, assignee_id, status,
                   design_summary, design_content, created_by, created_at, updated_at
            FROM requirement_design_module
            WHERE id = #{id} AND delivery_id = #{deliveryId}
            """)
    RequirementDesignModuleData selectByIdAndDelivery(long id, long deliveryId);

    @Insert("""
            INSERT INTO requirement_design_module (
                delivery_id, name, description, assignee_id, status,
                design_summary, design_content, created_by, created_at, updated_at
            ) VALUES (
                #{deliveryId}, #{name}, #{description}, #{assigneeId}, #{status},
                #{designSummary}, #{designContent}, #{createdBy}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RequirementDesignModuleData data);

    @Update("""
            UPDATE requirement_design_module
            SET name = #{name},
                description = #{description},
                assignee_id = #{assigneeId},
                status = #{status},
                design_summary = #{designSummary},
                design_content = #{designContent},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(RequirementDesignModuleData data);

    @Delete("DELETE FROM requirement_design_module WHERE id = #{id}")
    int delete(long id);

    @Update("""
            UPDATE requirement_design_module
            SET status = 'REVISION_REQUIRED', updated_at = UTC_TIMESTAMP(3)
            WHERE delivery_id = #{deliveryId} AND status = 'SUBMITTED'
            """)
    int reopenAll(long deliveryId);
}
