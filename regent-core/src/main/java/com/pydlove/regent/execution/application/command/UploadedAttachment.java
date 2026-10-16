package com.pydlove.regent.execution.application.command;

/**
 * 上传上来的一张图片。
 *
 * <p>刻意不是 Spring 的 {@code MultipartFile}：那一层留在接口层，应用层拿到的必须是
 * 「文件名 + 类型 + 字节」这三样纯数据，否则 regent-core 又要有框架依赖了。
 */
public record UploadedAttachment(String filename, String contentType, byte[] content) {
}
