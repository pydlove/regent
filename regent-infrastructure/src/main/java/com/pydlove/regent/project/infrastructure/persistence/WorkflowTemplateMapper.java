package com.pydlove.regent.project.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkflowTemplateMapper {

    @Select("""
            SELECT id, template_code, version_no, name, description, status
            FROM workflow_template
            ORDER BY updated_at DESC, template_code, version_no DESC
            """)
    List<WorkflowTemplateData> selectAll();

    @Select("""
            SELECT id, template_code, version_no, name, description, status
            FROM workflow_template
            WHERE status = 'PUBLISHED'
            ORDER BY template_code, version_no DESC
            """)
    List<WorkflowTemplateData> selectPublished();

    @Select("""
            SELECT id, template_code, version_no, name, description, status
            FROM workflow_template
            WHERE id = #{id}
            """)
    WorkflowTemplateData selectById(long id);

    @Select("""
            SELECT id, template_code, version_no, name, description, status
            FROM workflow_template
            WHERE status = 'PUBLISHED'
            ORDER BY
                CASE WHEN template_code = 'standard_waterfall' THEN 0 ELSE 1 END,
                template_code,
                version_no DESC
            LIMIT 1
            """)
    WorkflowTemplateData selectDefault();

    @Select("""
            SELECT COALESCE(MAX(version_no), 0) + 1
            FROM workflow_template
            WHERE template_code = #{code}
            """)
    int selectNextVersionNo(String code);

    @Insert("""
            INSERT INTO workflow_template
                (template_code, version_no, name, description, status, created_at, updated_at)
            VALUES
                (#{templateCode}, #{versionNo}, #{name}, #{description}, #{status},
                 UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTemplate(WorkflowTemplateData template);

    @Insert("""
            INSERT INTO workflow_template_stage
                (template_id, stage_code, stage_name, stage_order,
                 scope_code, approver_role, description)
            VALUES
                (#{templateId}, #{stageCode}, #{stageName}, #{stageOrder},
                 #{scopeCode}, #{approverRole}, #{description})
            """)
    int insertStage(WorkflowTemplateStageData stage);

    @Update("""
            UPDATE workflow_template
            SET status = 'SUPERSEDED', updated_at = #{updatedAt}
            WHERE template_code = #{code} AND status = 'PUBLISHED'
            """)
    int supersedePublished(@Param("code") String code,
                           @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE workflow_template
            SET status = #{status}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") long id,
                     @Param("status") String status,
                     @Param("updatedAt") LocalDateTime updatedAt);

    @Select("""
            SELECT
                (SELECT COUNT(*) FROM project_workflow_binding WHERE template_id = #{templateId})
                +
                (SELECT COUNT(*) FROM product_version_workflow_binding WHERE template_id = #{templateId})
            """)
    int countBindings(long templateId);

    @Select("""
            SELECT id, template_id, stage_code, stage_name, stage_order,
                   scope_code, approver_role, description
            FROM workflow_template_stage
            WHERE template_id = #{templateId}
            ORDER BY stage_order, id
            """)
    List<WorkflowTemplateStageData> selectStages(long templateId);

    @Select("""
            SELECT template_id
            FROM project_workflow_binding
            WHERE project_id = #{projectId}
            """)
    Long selectProjectTemplateId(long projectId);

    @Select("""
            SELECT template_id
            FROM product_version_workflow_binding
            WHERE product_version_id = #{productVersionId}
            """)
    Long selectVersionTemplateId(long productVersionId);

    @Insert("""
            INSERT INTO project_workflow_binding (project_id, template_id, selected_at)
            VALUES (#{projectId}, #{templateId}, #{selectedAt})
            ON DUPLICATE KEY UPDATE
                template_id = VALUES(template_id),
                selected_at = VALUES(selected_at)
            """)
    int bindProject(@Param("projectId") long projectId,
                    @Param("templateId") long templateId,
                    @Param("selectedAt") LocalDateTime selectedAt);

    @Insert("""
            INSERT INTO product_version_workflow_binding
                (product_version_id, template_id, snapped_at)
            VALUES (#{productVersionId}, #{templateId}, #{snappedAt})
            ON DUPLICATE KEY UPDATE
                template_id = VALUES(template_id),
                snapped_at = VALUES(snapped_at)
            """)
    int bindVersion(@Param("productVersionId") long productVersionId,
                    @Param("templateId") long templateId,
                    @Param("snappedAt") LocalDateTime snappedAt);
}
