package com.pydlove.regent.project.application.query;

import java.util.List;

/**
 * 目录浏览的结果：当前位置、上一级、以及它的子目录。
 *
 * <p>只带「名字 + 绝对路径」两样：选择器要的就是这两样。大小、修改时间属于文件管理器，
 * 多带一个字段就多一处要跟着文件系统变化同步的东西。
 */
public record DirectoryListing(String path, String parent, List<Entry> entries, boolean truncated) {

    /**
     * 一个子目录。
     *
     * <p>{@code path} 是绝对路径，前端拿它直接回填输入框 —— 拼接路径的活不该在浏览器里做，
     * 那边不知道分隔符、也不知道符号链接。
     */
    public record Entry(String name, String path) {
    }
}
