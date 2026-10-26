package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/** 查询开发规范版本时同时带出文件库元数据。 */
@Data
public class CodeStandardVersionData {
    private Long id;
    private Long standardId;
    private Long projectId;
    private Integer versionNo;
    private Long libraryDocumentId;
    private String contentHash;
    private String filename;
    private String contentType;
    private Long sizeBytes;
    private String storagePath;
    private LocalDateTime createdAt;
}
