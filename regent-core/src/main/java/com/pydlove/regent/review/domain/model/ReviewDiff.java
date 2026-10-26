package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.List;

/**
 * 一次审阅要固定的代码事实。
 *
 * <p>完整 diff 与稳定哈希一起保存：哈希决定旧结论是否还能用于部署，正文让人能复核当时到底
 * 看到了什么。截断只影响正文展示，不影响哈希的可信度。
 */
public record ReviewDiff(
        String baseRef,
        String headRef,
        boolean gitRepository,
        String diffHash,
        List<ReviewDiffFile> files,
        int additions,
        int deletions,
        String content,
        boolean truncated) {

    public ReviewDiff {
        if (diffHash == null || !diffHash.matches("[0-9a-f]{64}")) {
            throw new DomainException("审阅 diff 哈希必须是 64 位小写十六进制");
        }
        files = files == null ? List.of() : files.stream()
                .sorted(java.util.Comparator.comparing(ReviewDiffFile::path))
                .toList();
        if (additions < 0 || deletions < 0) {
            throw new DomainException("审阅 diff 增删行数不能为负数");
        }
        if (gitRepository && (baseRef == null || baseRef.isBlank())) {
            throw new DomainException("Git 仓库的审阅快照必须带 base ref");
        }
        content = content == null ? "" : content;
    }

    public int fileCount() {
        return files.size();
    }
}
