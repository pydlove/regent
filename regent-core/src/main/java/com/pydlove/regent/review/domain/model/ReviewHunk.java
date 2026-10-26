package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 变更单元里的关键 unified diff hunk。 */
public record ReviewHunk(
        String filePath,
        int oldStart,
        int oldLines,
        int newStart,
        int newLines,
        String content) {

    public ReviewHunk {
        if (filePath == null || filePath.isBlank()) {
            throw new DomainException("hunk 文件路径不能为空");
        }
        filePath = filePath.replace('\\', '/').strip();
        if (oldStart < 0 || oldLines < 0 || newStart < 0 || newLines < 0) {
            throw new DomainException("hunk 行号与行数不能为负数");
        }
        if (content == null || content.isBlank()) {
            throw new DomainException("hunk 内容不能为空");
        }
        content = content.stripTrailing();
    }
}
