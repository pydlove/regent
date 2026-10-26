package com.pydlove.regent.delivery.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RequirementDesignAttachmentMapper {

    @Select("""
            SELECT id, delivery_id, module_id, filename, content_type,
                   size_bytes, storage_path, uploaded_by, created_at
            FROM requirement_design_attachment
            WHERE id = #{id}
            """)
    RequirementDesignAttachmentData selectById(long id);

    @Select("""
            SELECT id, delivery_id, module_id, filename, content_type,
                   size_bytes, storage_path, uploaded_by, created_at
            FROM requirement_design_attachment
            WHERE delivery_id = #{deliveryId}
            ORDER BY id
            """)
    List<RequirementDesignAttachmentData> selectByDelivery(long deliveryId);

    @Select("""
            SELECT id, delivery_id, module_id, filename, content_type,
                   size_bytes, storage_path, uploaded_by, created_at
            FROM requirement_design_attachment
            WHERE module_id = #{moduleId}
            ORDER BY id
            """)
    List<RequirementDesignAttachmentData> selectByModule(long moduleId);

    @Insert("""
            INSERT INTO requirement_design_attachment (
                delivery_id, module_id, filename, content_type,
                size_bytes, storage_path, uploaded_by, created_at
            ) VALUES (
                #{deliveryId}, #{moduleId}, #{filename}, #{contentType},
                #{sizeBytes}, #{storagePath}, #{uploadedBy}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RequirementDesignAttachmentData data);
}
