package com.pydlove.regent.review.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code review_finding} 表的数据对象。 */
@Data
public class ReviewFindingData {

    private Long id;
    private Long snapshotId;
    private Long changeUnitId;
    private String severity;
    private String status;
    private String title;
    private String description;
    private String impact;
    private String filePath;
    private Integer lineStart;
    private Integer lineEnd;
    private String suggestedAction;
    private Long evidenceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
