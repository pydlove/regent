package com.pydlove.regent.delivery.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

public interface BuildArtifactMapper {
    @Select("""
            SELECT id, project_id, version, git_commit, digest, size_bytes, storage_ref,
                   built_by, built_at
            FROM build_artifact
            WHERE id = #{id}
            """)
    BuildArtifactData selectById(long id);

    @Insert("""
            INSERT INTO build_artifact (
                project_id, version, git_commit, digest, size_bytes, storage_ref, built_by, built_at
            ) VALUES (
                #{projectId}, #{version}, #{gitCommit}, #{digest}, #{sizeBytes},
                #{storageRef}, #{builtBy}, #{builtAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BuildArtifactData data);
}
