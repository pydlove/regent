package com.pydlove.regent.project.infrastructure.persistence;

import lombok.Data;

/** {@code environment_entry_project} 的环境到项目关联。 */
@Data
public class EnvironmentProjectRow {

    private Long environmentId;
    private Long projectId;
}
