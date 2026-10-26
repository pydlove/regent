package com.pydlove.regent.execution.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行记录写侧的 MyBatis 映射接口。SQL 全部在 {@code mapper/TaskRunMapper.xml}。
 *
 * <p>列表与详情都用同一套列：一个任务最多几十次尝试，没有必要为「列表少两列」再开一套读模型。
 */
public interface TaskRunMapper {

    TaskRunData selectById(@Param("id") long id);

    TaskRunData selectByIdOwned(@Param("id") long id, @Param("ownerId") long ownerId);

    TaskRunData selectLatestByTask(@Param("taskId") long taskId);

    List<TaskRunData> selectByTask(@Param("taskId") long taskId);

    List<TaskRunData> selectRunningInternal(@Param("executor") String executor);

    List<TaskRunData> selectExpiredLeases(@Param("executor") String executor,
                                          @Param("now") LocalDateTime now);

    List<TaskRunData> selectActiveLeases(@Param("executor") String executor,
                                         @Param("now") LocalDateTime now);

    List<TaskRunData> selectRunningByDevice(@Param("deviceId") String deviceId);

    int insert(TaskRunData data);

    int update(TaskRunData data);

    int updateLease(@Param("id") long id,
                    @Param("deviceId") String deviceId,
                    @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
                    @Param("lastHeartbeatAt") LocalDateTime lastHeartbeatAt);

    int updateLastEvent(@Param("id") long id,
                        @Param("lastEventAt") LocalDateTime lastEventAt);
}
