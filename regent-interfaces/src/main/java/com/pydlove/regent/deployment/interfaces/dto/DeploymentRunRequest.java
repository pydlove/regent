package com.pydlove.regent.deployment.interfaces.dto;

/** 指定本次部署使用哪一个关联项目的工作区。 */
public record DeploymentRunRequest(Long projectId, Long taskId) {
}
