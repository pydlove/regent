package com.pydlove.regent.requirement.infrastructure.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface RequirementPurgeMapper {

    @Delete("""
            <script>
            DELETE ri
            FROM release_item ri
            JOIN requirement_delivery rd ON rd.id = ri.requirement_delivery_id
            JOIN requirement r ON r.id = rd.requirement_id
            WHERE r.project_id = #{projectId}
              AND r.deleted = 1
              AND r.id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            </script>
            """)
    int deleteReleaseItems(@Param("projectId") long projectId,
                           @Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE dg
            FROM delivery_gate dg
            JOIN requirement_delivery rd
              ON rd.id = dg.scope_id AND dg.scope_type = 'REQUIREMENT_DELIVERY'
            JOIN requirement r ON r.id = rd.requirement_id
            WHERE r.project_id = #{projectId}
              AND r.deleted = 1
              AND r.id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            </script>
            """)
    int deleteDeliveryGates(@Param("projectId") long projectId,
                            @Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE rd
            FROM requirement_delivery rd
            JOIN requirement r ON r.id = rd.requirement_id
            WHERE r.project_id = #{projectId}
              AND r.deleted = 1
              AND r.id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            </script>
            """)
    int deleteRequirementDeliveries(@Param("projectId") long projectId,
                                    @Param("ids") List<Long> ids);

    @Update("""
            <script>
            UPDATE task t
            JOIN requirement r ON r.id = t.requirement_id
            SET t.requirement_id = NULL,
                t.updated_at = UTC_TIMESTAMP(3)
            WHERE r.project_id = #{projectId}
              AND r.deleted = 1
              AND r.id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            </script>
            """)
    int detachTasks(@Param("projectId") long projectId,
                    @Param("ids") List<Long> ids);

    @Update("""
            <script>
            UPDATE bug b
            JOIN requirement r ON r.id = b.requirement_id
            SET b.requirement_id = NULL,
                b.updated_at = UTC_TIMESTAMP(3)
            WHERE r.project_id = #{projectId}
              AND r.deleted = 1
              AND r.id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            </script>
            """)
    int detachBugs(@Param("projectId") long projectId,
                   @Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE FROM requirement
            WHERE project_id = #{projectId}
              AND deleted = 1
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            </script>
            """)
    int deleteRequirements(@Param("projectId") long projectId,
                           @Param("ids") List<Long> ids);
}
