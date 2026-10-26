package com.pydlove.regent.project.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkflowTemplateMapper {

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
            WHERE id = #{id} AND status = 'PUBLISHED'
            """)
    WorkflowTemplateData selectPublishedById(long id);

    @Select("""
            SELECT id, template_code, version_no, name, description, status
            FROM workflow_template
            WHERE template_code = 'standard_waterfall' AND status = 'PUBLISHED'
            ORDER BY version_no DESC
            LIMIT 1
            """)
    WorkflowTemplateData selectDefault();

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
