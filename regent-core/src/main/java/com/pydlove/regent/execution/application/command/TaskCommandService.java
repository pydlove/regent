package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.project.domain.repository.ProjectProfileRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;

/**
 * 任务写侧用例。
 *
 * <p>这里不做状态判断 —— 判断全在聚合的 {@code requireState} 里。应用服务只负责三件事：
 * 取聚合、调领域方法、落库。多写一行 {@code if (status == ...)} 都是把状态机复制成两份。
 */
public class TaskCommandService {

    /** 列表里的一行放得下的字数。超了就截断 —— 截断只做一次，别让每个前端各截一遍。 */
    static final int TITLE_MAX = 60;

    private final TaskRepository taskRepository;
    private final ProjectProfileRepository projectProfileRepository;

    public TaskCommandService(TaskRepository taskRepository,
                              ProjectProfileRepository projectProfileRepository) {
        this.taskRepository = taskRepository;
        this.projectProfileRepository = projectProfileRepository;
    }

    /**
     * 人工录入任务（来源 MANUAL）。
     *
     * <p>项目必须已注册：task.project_id 有外键，硬插不存在的项目会得到一句数据库报错，
     * 在这里先查一次，报错才是「项目不存在：#12」这种能直接行动的话。
     *
     * <p>不拦「项目已暂停」：暂停拦的是派发，不是录入 —— 排队等复工的任务是合理需求，
     * 该拦住它的是 {@code ProjectProfile.requireDispatchable()}。
     */
    public Task createManual(CreateTaskCommand command) {
        projectProfileRepository.findById(command.projectId())
                .orElseThrow(() -> new EntityNotFoundException("项目不存在：" + command.projectId()));
        Task task = Task.manual(command.projectId(), deriveTitle(command.prompt()), command.prompt(),
                command.priority());
        taskRepository.save(task);
        return task;
    }

    /**
     * 标题从指令的首个非空行推导 —— 录入时只让人写一遍任务。
     *
     * <p>行首的 Markdown 记号（{@code #}、{@code >}、{@code -}、{@code 1.}）要剥掉：指令常常是从
     * 别处粘过来的一段带格式文本，标题里留着「## 」只会让列表看起来脏。剥记号时要求记号后跟空白，
     * 免得把「-5 度以下要告警」这种正常的开头也削掉。超过 {@value #TITLE_MAX} 字截断加省略号。
     */
    private static String deriveTitle(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new DomainException("执行指令不能为空");
        }
        for (String line : prompt.split("\\R")) {
            String cleaned = line.strip()
                    .replaceFirst("^[#>]+\\s*", "")
                    .replaceFirst("^([-*+]|\\d+[.、)])\\s+", "")
                    .strip();
            if (!cleaned.isEmpty()) {
                return cleaned.length() <= TITLE_MAX ? cleaned : cleaned.substring(0, TITLE_MAX) + "…";
            }
        }
        throw new DomainException("执行指令不能为空");
    }

    public Task retry(TaskId id) {
        Task task = load(id);
        task.retry();
        taskRepository.save(task);
        return task;
    }

    public Task cancel(TaskId id) {
        Task task = load(id);
        task.cancel();
        taskRepository.save(task);
        return task;
    }

    private Task load(TaskId id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("任务不存在：" + id));
    }
}
