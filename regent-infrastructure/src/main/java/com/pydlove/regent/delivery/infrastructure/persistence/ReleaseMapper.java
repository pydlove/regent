package com.pydlove.regent.delivery.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface ReleaseMapper {

    @Select("""
            SELECT id, project_id, version, attempt_no, build_artifact_id, stage, status,
                   release_notes, rollback_plan, created_at, updated_at
            FROM `release`
            WHERE id = #{id}
            """)
    ReleaseData selectById(long id);

    @Select("""
            SELECT id, project_id, version, attempt_no, build_artifact_id, stage, status,
                   release_notes, rollback_plan, created_at, updated_at
            FROM `release`
            WHERE project_id = #{projectId}
            ORDER BY id DESC
            """)
    List<ReleaseData> selectByProject(long projectId);

    @Select("""
            SELECT COALESCE(MAX(attempt_no), 0) + 1
            FROM `release`
            WHERE project_id = #{projectId} AND version = #{version}
            """)
    int nextAttemptNo(@Param("projectId") long projectId, @Param("version") String version);

    @Insert("""
            INSERT INTO `release` (
                project_id, version, attempt_no, build_artifact_id, stage, status,
                release_notes, rollback_plan, created_at, updated_at
            ) VALUES (
                #{projectId}, #{version}, #{attemptNo}, #{buildArtifactId}, #{stage}, #{status},
                #{releaseNotes}, #{rollbackPlan}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReleaseData data);

    @Update("""
            UPDATE `release`
            SET stage = #{stage},
                status = #{status},
                release_notes = #{releaseNotes},
                rollback_plan = #{rollbackPlan},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(ReleaseData data);

    @Select("""
            SELECT requirement_delivery_id
            FROM release_item
            WHERE release_id = #{releaseId}
            ORDER BY requirement_delivery_id
            """)
    List<Long> selectDeliveryIds(long releaseId);

    @Insert("""
            INSERT INTO release_item (release_id, requirement_delivery_id, created_at)
            VALUES (#{releaseId}, #{deliveryId}, #{createdAt})
            """)
    int insertItem(@Param("releaseId") long releaseId,
                   @Param("deliveryId") long deliveryId,
                   @Param("createdAt") LocalDateTime createdAt);
}
