package com.pydlove.regent.delivery.application.command;

/** 接口层传入的立项纪要附件原始内容。 */
public record UploadedInitiativeAttachment(String filename, String contentType, byte[] content) {
}
