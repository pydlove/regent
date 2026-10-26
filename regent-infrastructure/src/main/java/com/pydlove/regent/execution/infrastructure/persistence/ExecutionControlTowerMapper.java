package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 执行控制塔联合只读查询。 */
public interface ExecutionControlTowerMapper {

    List<ExecutionControlTowerRowData> selectEntries(
            @Param("ownerId") long ownerId,
            @Param("projectId") Long projectId,
            @Param("limit") int limit);
}
