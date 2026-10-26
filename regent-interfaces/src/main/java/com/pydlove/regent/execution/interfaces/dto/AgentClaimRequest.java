package com.pydlove.regent.execution.interfaces.dto;

/**
 * 认领请求。
 *
 * <p>四个字段都不是技术参数，而是责任与范围的依据：谁在干活、活交回哪个会话、
 * 在哪个项目里找活、要不要指定某一条。
 *
 * <p>{@code projectCode} 是「把地址和项目交给执行者」这句话的落点：人只说得出项目标识
 * （{@code regent}），说不出内部 ID。为空表示不限项目，由平台在全部候选里挑。
 */
public record AgentClaimRequest(String executor,
                                String sessionId,
                                String projectCode,
                                Long taskId,
                                String deviceId) {

    public AgentClaimRequest(String executor, String sessionId, String projectCode, Long taskId) {
        this(executor, sessionId, projectCode, taskId, null);
    }
}
