package com.pydlove.regent.delivery.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ProductVersionMapper {

    @Select("""
            SELECT id, project_id, version_no, name, description, status,
                   created_by, created_at, updated_at
            FROM product_version
            WHERE id = #{id}
            """)
    ProductVersionData selectById(long id);

    @Select("""
            SELECT id, project_id, version_no, name, description, status,
                   created_by, created_at, updated_at
            FROM product_version
            WHERE project_id = #{projectId}
            ORDER BY id DESC
            """)
    List<ProductVersionData> selectByProject(long projectId);

    @Select("""
            SELECT COUNT(*)
            FROM product_version
            WHERE project_id = #{projectId} AND version_no = #{versionNo}
            """)
    int countByProjectAndVersionNo(
            @Param("projectId") long projectId,
            @Param("versionNo") String versionNo);

    @Insert("""
            INSERT INTO product_version (
                project_id, version_no, name, description, status,
                created_by, created_at, updated_at
            ) VALUES (
                #{projectId}, #{versionNo}, #{name}, #{description}, #{status},
                #{createdBy}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductVersionData data);

    @Update("""
            UPDATE product_version
            SET name = #{name},
                description = #{description},
                status = #{status},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(ProductVersionData data);
}
