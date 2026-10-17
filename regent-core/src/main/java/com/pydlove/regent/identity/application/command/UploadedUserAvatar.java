package com.pydlove.regent.identity.application.command;

/** 尚未校验和落盘的用户头像原始内容。 */
public record UploadedUserAvatar(String filename, String contentType, byte[] content) {
}
