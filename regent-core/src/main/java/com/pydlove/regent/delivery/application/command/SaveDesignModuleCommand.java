package com.pydlove.regent.delivery.application.command;

import com.pydlove.regent.identity.domain.model.UserId;

/** 技术负责人新建或调整模块负责人。 */
public record SaveDesignModuleCommand(
        String name,
        String description,
        UserId assigneeId) {
}
