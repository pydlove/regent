package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.RunEvent;
import com.pydlove.regent.execution.domain.model.TaskRunId;

import java.util.List;

/**
 * 执行事件流仓储 —— 只追加、只按运行读，没有更新与删除。
 *
 * <p>单独成接口而不是塞进 {@link TaskRunRepository}：事件的写入频率比聚合高两个数量级
 * （进程每输出一行就写一次），聚合那边是「开局一行、收尾一行」。合成一个接口后，
 * 两侧的批次策略迟早会互相迁就。
 */
public interface RunEventRepository {

    /** 追加一条事件。序号由调用方保证在一次运行内连续、不重复。 */
    void append(TaskRunId runId, RunEvent event);

    List<RunEvent> findByRun(TaskRunId runId);
}
