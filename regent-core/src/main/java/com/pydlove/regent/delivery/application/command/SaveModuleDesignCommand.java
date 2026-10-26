package com.pydlove.regent.delivery.application.command;

/** 研发或技术负责人保存模块详细设计。 */
public record SaveModuleDesignCommand(
        String designSummary,
        String designContent) {
}
