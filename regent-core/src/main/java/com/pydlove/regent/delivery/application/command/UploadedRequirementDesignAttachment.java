package com.pydlove.regent.delivery.application.command;

/** 概要设计或模块设计上传文件。 */
public record UploadedRequirementDesignAttachment(
        String filename,
        String contentType,
        byte[] content) {
}
