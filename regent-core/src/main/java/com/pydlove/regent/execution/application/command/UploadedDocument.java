package com.pydlove.regent.execution.application.command;

/** 上传上来的文档，接口层的 MultipartFile 已在边界转成纯数据。 */
public record UploadedDocument(String filename, String contentType, byte[] content) {
}
