package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

/** 一个变更文件的元信息；二进制文件只保留统计，不进入人工主审代码。 */
public record ReviewDiffFile(
        String path,
        int additions,
        int deletions,
        boolean binary,
        boolean generated) {

    public ReviewDiffFile {
        if (path == null || path.isBlank()) {
            throw new DomainException("变更文件路径不能为空");
        }
        path = path.replace('\\', '/').strip();
        if (path.startsWith("/") || path.contains("../")) {
            throw new DomainException("变更文件路径必须位于项目内：" + path);
        }
        if (additions < 0 || deletions < 0) {
            throw new DomainException("文件增删行数不能为负数：" + path);
        }
    }

    public int changedLines() {
        return additions + deletions;
    }
}
