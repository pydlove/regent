package com.pydlove.regent.execution.infrastructure.persistence;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code task} 表的数据对象 —— 一行一条，字段与列一一对应，不做任何加工。
 *
 * <p>它和领域对象 {@code Task} 刻意分开：领域对象有行为和不变量，且字段是按聚合边界组织的；
 * 数据对象只跟着表结构走。把两者合并成一个类，等于让表结构直接决定聚合的形状，
 * 以后改一处列名就要动领域代码。
 *
 * <p>表里的 {@code deleted} 不进领域对象：逻辑删除是实现细节，领域层不需要知道
 * 「记录还在库里但被标记为删除」这回事，它只该见到删掉之后的世界。
 *
 * <p>时间列用 {@link LocalDateTime} 而不是 {@code Instant}：按约定这些列存的就是 UTC 字面量，
 * 显式转换（见 {@link TaskDataMapper}）比交给 JDBC 驱动去猜时区可控得多 —— 驱动行为会随
 * 版本与连接串参数变化，而「什么时区」这种事一旦漂移，只会在几个月后对账时才被发现。
 */
@Data
public class TaskData {

    private Long id;
    private Long projectId;
    private Long requirementId;
    private Long bugId;
    private String source;
    private String title;
    private String prompt;
    private String status;
    private Integer priority;
    private String codexThreadId;
    private String branchName;
    private String branchApprovalState;
    private Long blockingDecisionId;
    private Integer attemptCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
