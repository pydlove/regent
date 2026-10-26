package com.pydlove.regent.delivery.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InitiativeAttachmentMapper {

    @Insert("""
            INSERT INTO initiative_attachment (
                initiative_id, filename, content_type, size_bytes, storage_path,
                uploaded_by, created_at
            ) VALUES (
                #{initiativeId}, #{filename}, #{contentType}, #{sizeBytes}, #{storagePath},
                #{uploadedBy}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InitiativeAttachmentData data);

    @Select("""
            SELECT id, initiative_id, filename, content_type, size_bytes, storage_path,
                   uploaded_by, created_at
            FROM initiative_attachment
            WHERE id = #{id}
            """)
    InitiativeAttachmentData selectById(long id);

    @Select("""
            SELECT a.id, a.initiative_id, a.filename, a.content_type, a.size_bytes,
                   a.storage_path, a.uploaded_by, a.created_at
            FROM initiative_attachment a
            JOIN initiative i ON i.id = a.initiative_id
            JOIN project p ON p.id = i.project_id
            WHERE a.id = #{id}
              AND p.deleted = 0
              AND (
                p.owner_id = #{ownerId}
                OR EXISTS (
                  SELECT 1 FROM project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{ownerId}
                )
              )
            """)
    InitiativeAttachmentData selectByIdOwned(
            @Param("id") long id, @Param("ownerId") long ownerId);

    @Select("""
            SELECT id, initiative_id, filename, content_type, size_bytes, storage_path,
                   uploaded_by, created_at
            FROM initiative_attachment
            WHERE initiative_id = #{initiativeId}
            ORDER BY id
            """)
    List<InitiativeAttachmentData> selectByInitiative(long initiativeId);
}
