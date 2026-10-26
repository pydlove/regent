package com.pydlove.regent.review.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code review_snapshot} 表的数据对象。 */
@Data
public class ReviewSnapshotData {

    private Long id;
    private Long taskId;
    private Long taskRunId;
    private Long projectId;
    private String baseRef;
    private String headRef;
    private Boolean gitRepository;
    private String diffHash;
    private Integer fileCount;
    private Integer additionCount;
    private Integer deletionCount;
    private String filesJson;
    private String status;
    private String diffContent;
    private Boolean diffTruncated;
    private String analysisStatus;
    private String analysisError;
    private String commitSha;
    private String commitMessage;
    private Long committedBy;
    private LocalDateTime committedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
