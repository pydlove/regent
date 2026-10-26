package com.pydlove.regent.project.infrastructure.persistence;

import lombok.Data;

/** workflow_template 表读写对象。 */
@Data
public class WorkflowTemplateData {
    private Long id;
    private String templateCode;
    private Integer versionNo;
    private String name;
    private String description;
    private String status;
}
