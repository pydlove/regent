package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 执行事件流的 MyBatis 映射接口。只追加、只按运行读。 */
public interface RunEventMapper {

    int insert(RunEventData data);

    List<RunEventData> selectByRun(@Param("taskRunId") long taskRunId);
}
