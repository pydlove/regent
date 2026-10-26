package com.pydlove.regent.review.domain.model;

import com.pydlove.regent.shared.domain.DomainException;

import java.util.List;

/** Git 适配器需要的只读上下文。 */
public record ReviewDiffRequest(
        String projectDirectory,
        String baseRef,
        List<String> reportedChangedFiles) {

    public ReviewDiffRequest {
        if (projectDirectory == null || projectDirectory.isBlank()) {
            throw new DomainException("审阅项目目录不能为空");
        }
        reportedChangedFiles = reportedChangedFiles == null
                ? List.of()
                : reportedChangedFiles.stream()
                        .filter(path -> path != null && !path.isBlank())
                        .map(String::strip)
                        .distinct()
                        .toList();
    }
}
