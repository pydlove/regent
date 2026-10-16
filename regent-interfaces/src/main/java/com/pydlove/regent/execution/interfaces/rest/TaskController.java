package com.pydlove.regent.execution.interfaces.rest;

import com.pydlove.regent.execution.application.command.CreateTaskCommand;
import com.pydlove.regent.execution.application.command.TaskCommandService;
import com.pydlove.regent.execution.application.command.TaskDispatchService;
import com.pydlove.regent.execution.application.query.TaskQueryService;
import com.pydlove.regent.execution.application.query.TaskRunQueryService;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskListItemPage;
import com.pydlove.regent.execution.domain.repository.TaskQuery;
import com.pydlove.regent.execution.interfaces.dto.TaskCreateRequest;
import com.pydlove.regent.execution.interfaces.dto.TaskDetailResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskRunSummaryResponse;
import com.pydlove.regent.execution.interfaces.dto.TaskSummaryResponse;
import com.pydlove.regent.interfaces.dto.PageResponse;
import com.pydlove.regent.project.domain.model.ProjectId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
                                                  @RequestParam(required = false) Long projectId,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer size) {
        TaskListItemPage result = taskQueryService.list(TaskQuery.of(status, projectId, keyword, page, size));
        return PageResponse.of(
                result.items().stream().map(TaskSummaryResponse::from).toList(),
                result.total(), result.page(), result.size());
    }

    /**
     * 状态分布。返回固定六个键（缺失的状态为 0），前端可以直接按状态名取数，
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

    /** 人工重试：FAILED → PENDING。守卫在聚合里，这里不做判断。 */
    @PostMapping("/{id}/retry")
    public TaskDetailResponse retry(@PathVariable long id) {
        return TaskDetailResponse.from(taskCommandService.retry(TaskId.of(id)));
    }

    /** 终止任务：非终态 → CANCELLED。 */
    @PostMapping("/{id}/cancel")
    public TaskDetailResponse cancel(@PathVariable long id) {
        return TaskDetailResponse.from(taskCommandService.cancel(TaskId.of(id)));
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

    /** 运行记录：每次尝试一行，最新的在前。 */
    @GetMapping("/{id}/runs")
    public List<TaskRunSummaryResponse> runs(@PathVariable long id) {
        return taskRunQueryService.listByTask(TaskId.of(id)).stream()
                .map(TaskRunSummaryResponse::from)
                .toList();
    }
}
