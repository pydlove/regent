package com.pydlove.regent.bug.application.command;

/** 从接口层传进应用层的图片数据，不携带 Spring 类型。 */
public record UploadedBugAttachment(String filename, String contentType, byte[] content) {
}
