package com.pydlove.regent.delivery.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface InitiativeMapper {

    @Select("""
            SELECT id, project_id, version_id, created_by, title, business_goal, scope_text,
                   out_of_scope_text, success_metrics, stakeholders, target_version, risk_text,
                   meeting_minutes,
                   status, submitted_at, approved_at,
                   rejected_at, created_at, updated_at
            FROM initiative
            WHERE id = #{id}
            """)
    InitiativeData selectById(long id);

    @Select("""
            SELECT id, project_id, version_id, created_by, title, business_goal, scope_text,
                   out_of_scope_text, success_metrics, stakeholders, target_version, risk_text,
                   meeting_minutes,
                   status, submitted_at, approved_at,
                   rejected_at, created_at, updated_at
            FROM initiative
            WHERE project_id = #{projectId}
            ORDER BY id DESC
            """)
    List<InitiativeData> selectByProject(long projectId);

    @Select("""
            SELECT id, project_id, version_id, created_by, title, business_goal, scope_text,
                   out_of_scope_text, success_metrics, stakeholders, target_version, risk_text,
                   meeting_minutes,
                   status, submitted_at, approved_at,
                   rejected_at, created_at, updated_at
            FROM initiative
            WHERE version_id = #{versionId}
            """)
    InitiativeData selectByVersion(long versionId);

    @Insert("""
            INSERT INTO initiative (
                project_id, version_id, created_by, title, business_goal, scope_text,
                out_of_scope_text, success_metrics, stakeholders, target_version, risk_text,
                meeting_minutes,
                status, submitted_at, approved_at,
                rejected_at, created_at, updated_at
            ) VALUES (
                #{projectId}, #{versionId}, #{createdBy}, #{title}, #{businessGoal}, #{scopeText},
                #{outOfScopeText}, #{successMetrics}, #{stakeholders}, #{targetVersion}, #{riskText},
                #{meetingMinutes},
                #{status}, #{submittedAt}, #{approvedAt},
                #{rejectedAt}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InitiativeData data);

    @Update("""
            UPDATE initiative
            SET title = #{title},
                business_goal = #{businessGoal},
                scope_text = #{scopeText},
                out_of_scope_text = #{outOfScopeText},
                success_metrics = #{successMetrics},
                stakeholders = #{stakeholders},
                target_version = #{targetVersion},
                risk_text = #{riskText},
                meeting_minutes = #{meetingMinutes},
                status = #{status},
                submitted_at = #{submittedAt},
                approved_at = #{approvedAt},
                rejected_at = #{rejectedAt},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(InitiativeData data);

    @Delete("""
            DELETE FROM delivery_gate
            WHERE scope_type = 'INITIATIVE'
              AND scope_id IN (
                  SELECT id FROM initiative WHERE version_id = #{versionId}
              )
            """)
    int deleteGatesByVersion(long versionId);

    @Delete("DELETE FROM initiative WHERE version_id = #{versionId}")
    int deleteByVersion(long versionId);
}
