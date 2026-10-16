package com.pydlove.regent.project.interfaces.dto;

import com.pydlove.regent.project.application.query.DirectoryListing;

import java.util.List;

/**
 * 目录浏览的对外契约。字段与 {@link DirectoryListing} 一一对应，不做改名 ——
 * 前端 {@code types/api.ts} 里的 DirectoryListing 逐字对齐这四个名字。
 *
 * <p>{@code parent} 在根目录上是 null，前端据此禁用「上一级」；
 * {@code truncated} 为 true 表示这个目录下的子目录比返回的多。
 */
public record DirectoryListingResponse(String path, String parent, List<Entry> entries, boolean truncated) {

    public record Entry(String name, String path) {
    }

    public static DirectoryListingResponse from(DirectoryListing listing) {
        return new DirectoryListingResponse(
                listing.path(),
                listing.parent(),
                listing.entries().stream()
                        .map(entry -> new Entry(entry.name(), entry.path()))
                        .toList(),
                listing.truncated());
    }
}
