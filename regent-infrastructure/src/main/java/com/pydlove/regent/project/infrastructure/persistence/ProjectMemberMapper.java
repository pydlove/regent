package com.pydlove.regent.project.infrastructure.persistence;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface ProjectMemberMapper {

    @Select("""
            SELECT id, project_id, user_id, role_code, created_at
            FROM project_member
            WHERE project_id = #{projectId}
            ORDER BY id
            """)
    List<ProjectMemberData> selectByProject(long projectId);

    @Select("""
            SELECT role_code
            FROM project_member
            WHERE project_id = #{projectId} AND user_id = #{userId}
            ORDER BY id
            """)
    List<String> selectRoles(@Param("projectId") long projectId, @Param("userId") long userId);

    @Select("""
            SELECT COUNT(*)
            FROM project_member
            WHERE project_id = #{projectId} AND user_id = #{userId} AND role_code = #{roleCode}
            """)
    int countRole(@Param("projectId") long projectId,
                  @Param("userId") long userId,
                  @Param("roleCode") String roleCode);

    @Select("""
            SELECT COUNT(DISTINCT user_id)
            FROM project_member
            WHERE project_id = #{projectId}
            """)
    int countDistinctUsers(long projectId);

    @Insert("""
            INSERT INTO project_member (project_id, user_id, role_code, created_at)
            VALUES (#{projectId}, #{userId}, #{roleCode}, #{createdAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProjectMemberData data);

    @Delete("""
            DELETE FROM project_member
            WHERE project_id = #{projectId} AND user_id = #{userId} AND role_code = #{roleCode}
            """)
    int delete(@Param("projectId") long projectId,
               @Param("userId") long userId,
               @Param("roleCode") String roleCode);

    @Insert("""
            INSERT IGNORE INTO project_member (project_id, user_id, role_code, created_at)
            VALUES (#{projectId}, #{userId}, #{roleCode}, #{createdAt})
            """)
    int ensure(@Param("projectId") long projectId,
               @Param("userId") long userId,
               @Param("roleCode") String roleCode,
               @Param("createdAt") LocalDateTime createdAt);
}
