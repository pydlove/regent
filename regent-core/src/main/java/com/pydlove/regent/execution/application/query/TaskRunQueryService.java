package com.pydlove.regent.execution.application.query;

import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskRun;
import com.pydlove.regent.execution.domain.model.TaskRunId;
import com.pydlove.regent.execution.domain.repository.RunEventRepository;
import com.pydlove.regent.execution.domain.repository.TaskRunRepository;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 执行记录查询。
 *
 * <p>不另开读模型：一个任务的尝试次数是两位数，详情页要的字段与聚合完全重合。
 * 为它造一套查询对象与投影，只会多出一处需要同步维护的字段列表。
 */
public class TaskRunQueryService {

    private final TaskRunRepository taskRunRepository;
    private final RunEventRepository runEventRepository;

    public TaskRunQueryService(TaskRunRepository taskRunRepository, RunEventRepository runEventRepository) {
        this.taskRunRepository = taskRunRepository;
        this.runEventRepository = runEventRepository;
    }

    /** 某个任务的全部尝试，最新的在前 —— 看日志的人关心的永远是最近一次。 */
    public List<TaskRun> listByTask(TaskId taskId) {
        List<TaskRun> runs = new ArrayList<>(taskRunRepository.listByTask(taskId));
        Collections.reverse(runs);
        return List.copyOf(runs);
    }

    public TaskRun get(TaskRunId id) {
        return taskRunRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("执行记录不存在：" + id));
    }

    public TaskRun get(TaskRunId id, UserId ownerId) {
        return taskRunRepository.findByIdOwned(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("执行记录不存在：" + id));
    }

    /** 事件流按序号升序 —— 它就是「当时发生了什么」的时间线，不能倒着读。 */
    public List<RunEvent> events(TaskRunId runId) {
        return runEventRepository.findByRun(runId);
    }
}
