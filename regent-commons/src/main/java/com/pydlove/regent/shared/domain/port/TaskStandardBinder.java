package com.pydlove.regent.shared.domain.port;

/**
 * 跨上下文创建任务后的默认规范绑定端口。
 *
 * <p>需求和缺陷上下文只负责在自己的事务里创建任务，不应直接依赖执行上下文的规范应用服务。
 */
public interface TaskStandardBinder {

    void bindDefaults(long taskId, long projectId);
}
