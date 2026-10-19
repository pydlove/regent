package com.pydlove.regent.project.domain.port;

import com.pydlove.regent.project.domain.model.ProjectId;

/** 项目注册成功后的资料生成调度口。 */
public interface ProjectSummaryScheduler {

    void schedule(ProjectId projectId);
}
