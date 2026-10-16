package com.pydlove.regent.interfaces.dto;

import java.util.List;

/**
 * 统一的分页响应体。
 *
 * <p>带上 {@code totalPages}：前端分页条要显示「第 3 / 12 页」，让它自己用总数算一遍
 * 等于把同一份取整逻辑复制到第二个地方，而取整的边界（最后一页不满）恰好最容易写错。
 */
public record PageResponse<T>(List<T> items, long total, int page, int size, int totalPages) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        int totalPages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResponse<>(items, total, page, size, totalPages);
    }
}
