package com.pydlove.regent.project.infrastructure.persistence;

import lombok.Data;

/**
 * {@code project} 表里执行链路真正需要的那些列。
 *
 * <p>不是整行：{@code tech_stack} / {@code verify_method} 之类属于画像展示，等需求拆解用得上时再加。
 * 一张表一个数据对象到底，会让每个使用方都拿得到自己不该关心的列。
 */
@Data
public class ProjectProfileRow {

    private Long id;
    private String code;
    private String name;
    private String path;
    private String defaultDecisionLevel;
    private Boolean enabled;
}
