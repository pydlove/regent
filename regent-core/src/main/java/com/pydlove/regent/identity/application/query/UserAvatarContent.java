package com.pydlove.regent.identity.application.query;

/** 可直接作为图片响应返回的头像字节。 */
public record UserAvatarContent(String contentType, byte[] content) {
}
