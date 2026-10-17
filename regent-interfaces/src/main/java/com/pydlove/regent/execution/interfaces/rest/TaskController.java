package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.CreateTaskCommand;
import com.pydlove.regent.execution.application.command.EditTaskCommand;
import com.pydlove.regent.execution.application.command.TaskCommandService;
import com.pydlove.regent.execution.application.command.TaskDeleteBatchResult;
import com.pydlove.regent.execution.application.command.TaskDispatchBatchResult;
import com.pydlove.regent.execution.application.command.TaskDispatchService;
import com.pydlove.regent.execution.application.query.TaskQueryService;
import com.pydlove.regent.execution.application.query.TaskRunQueryService;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskListItemPage;
import com.pydlove.regent.execution.domain.repository.TaskQuery;
import com.pydlove.regent.execution.interfaces.dto.TaskBatchDeleteRequest;
import com.pydlove.regent.execution.interfaces.dto.TaskBatchDeleteResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskBatchDispatchRequest;
import com.pydlove.regent.execution.interfaces.dto.TaskBatchDispatchResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskCreateRequest;
import com.pydlove.regent.execution.interfaces.dto.TaskDetailResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskRunSummaryResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskSummaryResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskTokenUsageResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskTrendPointResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskUpdateRequest;
import com.pydlove.regent.interfaces.dto.PageResponse;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务接口。
 *
 * <p>这一层只做三件事：把 URL 参数转成领域能接受的查询条件、调应用服务、把领域对象转成 DTO。
 * 任何 {@code if (status == ...)} 都不该出现在这里 —— 状态判断属于聚合，判断写在控制器里
 * 就等于把状态机复制到第二个地方，而两份状态机迟早会分叉。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskQueryService taskQueryService;
    private final TaskCommandService taskCommandService;
    private final TaskRunQueryService taskRunQueryService;
    private final TaskDispatchService taskDispatchService;

    public TaskController(TaskQueryService taskQueryService,
                          TaskCommandService taskCommandService,
                          TaskRunQueryService taskRunQueryService,
                          TaskDispatchService taskDispatchService) {
        this.taskQueryService = taskQueryService;
        this.taskCommandService = taskCommandService;
        this.taskRunQueryService = taskRunQueryService;
        this.taskDispatchService = taskDispatchService;
    }

    @GetMapping
    public PageResponse<TaskSummaryResponse> list(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String level,
                                                  @RequestParam(required = false) Long projectId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        TaskListItemPage result = taskQueryService.list(
                TaskQuery.of(status, level, projectId, keyword, page, size));
        return PageResponse.of(
                result.items().stream().map(TaskSummaryResponse::from).toList(),
                result.total(), result.page(), result.size());
    }

    /**
     * 状态分布。返回全部状态键（缺失的状态为 0），前端可以直接按状态名取数，
     * 不需要写「key 不存在时当作 0」的兜底。
     */
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        Map<String, Long> counts = new LinkedHashMap<>();
        taskQueryService.statusCounts()
                .forEach((status, count) -> counts.put(status.name(), count));
        for (TaskStatus status : TaskStatus.values()) {
            counts.putIfAbsent(status.name(), 0L);
        }
        return counts;
    }

    /** 看板 token 汇总。三个分量分开返回，页面可以解释消耗主要由哪一侧构成。 */
    @GetMapping("/token-usage")
    public TaskTokenUsageResponse tokenUsage() {
        return TaskTokenUsageResponse.from(taskQueryService.tokenUsage());
    }

    /**
     * 近 N 天收场趋势（含今天），日期连续、无数据的日子补 0，看板柱状图直接用。
     */
    @GetMapping("/trend")
    public List<TaskTrendPointResponse> trend(@RequestParam(defaultValue = "7") int days) {
        return taskQueryService.completionTrend(days).stream().map(TaskTrendPointResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TaskDetailResponse detail(@PathVariable long id) {
        return TaskDetailResponse.from(taskQueryService.get(TaskId.of(id)));
    }

    /**
     * 人工录入任务（来源 MANUAL）。
     *
     * <p>返回新建任务的详情：前端拿到它就能直接选中新行，不必再按标题回列表里找 ——
     * 列表带着分页与筛选，刚建的那条不在当前页是常态。
     */
    @PostMapping
    public TaskDetailResponse create(@RequestBody TaskCreateRequest request) {
        if (request.projectId() == null) {
            throw new IllegalArgumentException("缺少 projectId：任务必须挂在项目下");
        }
        Task task = taskCommandService.createManual(new CreateTaskCommand(
                ProjectId.of(request.projectId()),
                request.prompt(),
                request.priority() == null ? 0 : request.priority()));
        return TaskDetailResponse.from(task);
    }

    /** 编辑任务的指令与优先级。已完成任务由聚合守卫拒绝。 */
    @PutMapping("/{id}")
    public TaskDetailResponse update(@PathVariable long id, @RequestBody TaskUpdateRequest request) {
        return TaskDetailResponse.from(taskCommandService.edit(new EditTaskCommand(
                TaskId.of(id),
                request.prompt(),
                request.priority())));
    }

    /** 人工重试或重新执行：FAILED / CANCELLED → PENDING。守卫在聚合里，这里不做判断。 */
    @PostMapping("/{id}/retry")
    public TaskDetailResponse retry(@PathVariable long id) {
        return TaskDetailResponse.from(taskCommandService.retry(TaskId.of(id)));
    }

    /** 终止任务：非终态 → CANCELLED。 */
    @PostMapping("/{id}/cancel")
    public TaskDetailResponse cancel(@PathVariable long id) {
        return TaskDetailResponse.from(taskCommandService.cancel(TaskId.of(id)));
    }

    /** 删除待执行或排队中的任务。其他状态会被聚合守卫拒绝。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        taskCommandService.delete(TaskId.of(id));
    }

    /** 批量删除待执行或排队中的任务。单项失败通过响应体返回，不影响同一批里的其他任务。 */
    @PostMapping("/batch-delete")
    public TaskBatchDeleteResponse deleteBatch(@RequestBody TaskBatchDeleteRequest request) {
        if (request.taskIds() == null) {
            throw new IllegalArgumentException("缺少 taskIds：至少选择一个任务");
        }
        if (request.taskIds().contains(null)) {
            throw new IllegalArgumentException("任务 ID 不能为空");
        }
        TaskDeleteBatchResult result = taskCommandService.deleteBatch(
                request.taskIds().stream().map(TaskId::of).toList());
        return TaskBatchDeleteResponse.from(result);
    }

    /**
     * 派发执行。
     *
     * <p>返回的是「已认领」的状态（RUNNING），不是执行结果 —— 真正的执行在后台线程上跑，
     * 结果进执行记录，前端靠 {@code /runs} 面板看。同步等结果会让这个请求挂几十分钟。
     */
    @PostMapping("/{id}/dispatch")
    public TaskDetailResponse dispatch(@PathVariable long id) {
        return TaskDetailResponse.from(taskDispatchService.dispatch(TaskId.of(id)));
    }

    /** 批量派发任务。单项失败通过响应体返回，避免一个坏任务让整批请求失败。 */
    @PostMapping("/dispatch")
    public TaskBatchDispatchResponse dispatchBatch(@RequestBody TaskBatchDispatchRequest request) {
        if (request.taskIds() == null) {
            throw new IllegalArgumentException("缺少 taskIds：至少选择一个任务");
        }
        TaskDispatchBatchResult result = taskDispatchService.dispatchBatch(
                request.taskIds().stream().map(TaskId::of).toList());
        return TaskBatchDispatchResponse.from(result);
    }

    /** 运行记录：每次尝试一行，最新的在前。 */
    @GetMapping("/{id}/runs")
    public List<TaskRunSummaryResponse> runs(@PathVariable long id) {
        return taskRunQueryService.listByTask(TaskId.of(id)).stream()
                .map(TaskRunSummaryResponse::from)
                .toList();
    }
}
