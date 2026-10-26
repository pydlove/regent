package com.pydlove.regent.delivery.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface RequirementDeliveryMapper {

    @Select("""
            SELECT id, initiative_id, project_id, requirement_id, created_by, stage, status,
                   design_summary, design_content, outline_design_summary,
                   outline_design_content, outline_design_submitted_at,
                   outline_design_submitted_by, created_at, updated_at
            FROM requirement_delivery
            WHERE id = #{id}
            """)
    RequirementDeliveryData selectById(long id);

    @Select("""
            SELECT id, initiative_id, project_id, requirement_id, created_by, stage, status,
                   design_summary, design_content, outline_design_summary,
                   outline_design_content, outline_design_submitted_at,
                   outline_design_submitted_by, created_at, updated_at
            FROM requirement_delivery
            WHERE project_id = #{projectId}
            ORDER BY id DESC
            """)
    List<RequirementDeliveryData> selectByProject(long projectId);

    @Select("""
            <script>
            SELECT id, initiative_id, project_id, requirement_id, created_by, stage, status,
                   design_summary, design_content, outline_design_summary,
                   outline_design_content, outline_design_submitted_at,
                   outline_design_submitted_by, created_at, updated_at
            FROM requirement_delivery
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<RequirementDeliveryData> selectByIds(@Param("ids") List<Long> ids);

    @Select("""
            SELECT id, initiative_id, project_id, requirement_id, created_by, stage, status,
                   design_summary, design_content, outline_design_summary,
                   outline_design_content, outline_design_submitted_at,
                   outline_design_submitted_by, created_at, updated_at
            FROM requirement_delivery
            WHERE requirement_id = #{requirementId}
              AND status = 'ACTIVE'
            ORDER BY id DESC
            LIMIT 1
            """)
    RequirementDeliveryData selectActiveByRequirement(long requirementId);

    @Select("""
            SELECT EXISTS(
                SELECT 1
                FROM requirement_delivery d
                JOIN requirement r ON r.id = d.requirement_id
                WHERE r.version_id = #{versionId}
                  AND d.stage <> 'REQUIREMENT_REVIEW'
            )
            """)
    boolean existsStartedDesignForVersion(long versionId);

    @Insert("""
            INSERT INTO requirement_delivery (
                initiative_id, project_id, requirement_id, created_by, stage, status,
                design_summary, design_content, outline_design_summary,
                outline_design_content, outline_design_submitted_at,
                outline_design_submitted_by, created_at, updated_at
            ) VALUES (
                #{initiativeId}, #{projectId}, #{requirementId}, #{createdBy}, #{stage}, #{status},
                #{designSummary}, #{designContent}, #{outlineDesignSummary},
                #{outlineDesignContent}, #{outlineDesignSubmittedAt},
                #{outlineDesignSubmittedBy}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RequirementDeliveryData data);

    @Update("""
            UPDATE requirement_delivery
            SET stage = #{stage},
                status = #{status},
                design_summary = #{designSummary},
                design_content = #{designContent},
                outline_design_summary = #{outlineDesignSummary},
                outline_design_content = #{outlineDesignContent},
                outline_design_submitted_at = #{outlineDesignSubmittedAt},
                outline_design_submitted_by = #{outlineDesignSubmittedBy},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(RequirementDeliveryData data);
}
