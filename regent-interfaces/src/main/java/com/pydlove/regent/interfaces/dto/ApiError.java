package com.pydlove.regent.interfaces.dto;

import java.time.Instant;

/**
 * 错误响应体。
 *
 * <p>{@code code} 是给程序看的稳定标识（前端按它决定是否重试、是否提示），
 * {@code message} 是给人看的说明。不要把两者混成一个字段 —— 一旦前端开始匹配中文提示，
 * 改一句措辞就会静默改掉行为。
 */
public record ApiError(String code, String message, Instant timestamp) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now());
    }
}
