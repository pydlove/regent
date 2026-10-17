package com.pydlove.regent.identity.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;

/** 用户头像的公开元数据与平台侧存储位置。 */
public record UserAvatar(
        String filename,
        String contentType,
        String storagePath,
        Instant updatedAt) {

    public UserAvatar {
        if (filename == null || filename.isBlank()) {
            throw new DomainException("头像文件名不能为空");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new DomainException("头像类型不能为空");
        }
        if (storagePath == null || storagePath.isBlank()) {
            throw new DomainException("头像存储路径不能为空");
        }
        Objects.requireNonNull(updatedAt, "头像更新时间");
    }
}
