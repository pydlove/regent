package com.pydlove.regent.deployment.infrastructure.persistence;

import lombok.Data;

/** {@code deployment_project} 的部署到项目关联。 */
@Data
public class DeploymentProjectRow {

    private Long deploymentId;
    private Long projectId;
}
