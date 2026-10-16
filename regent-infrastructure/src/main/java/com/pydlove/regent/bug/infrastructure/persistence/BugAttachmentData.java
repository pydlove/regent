package com.pydlove.regent.bug.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** {@code bug_attachment} 表的数据对象。 */
@Data
public class BugAttachmentData {

    private Long id;
    private Long bugId;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String storagePath;
    private LocalDateTime createdAt;
}
