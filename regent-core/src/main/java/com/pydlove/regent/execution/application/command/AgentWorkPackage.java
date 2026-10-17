package com.pydlove.regent.execution.application.command;

import java.time.Instant;
import java.util.List;

/**
 * 交给外部执行者的一份「工作包」—— 认领成功时平台能告诉它的全部事实。
 *
 * <p>它刻意做成一包给全：外部执行者可能在另一个进程、另一台机器上，让它在开工前
 * 再来问三次「分支是哪条」「图片在哪」「分寸是多少」，只会多出三个会挂的地方。
 *
 * @param protocol          协议版本；执行者看到不认识的版本应当拒绝开工
 * @param taskId            任务 ID
 * @param runId             本次尝试的执行记录 ID —— 上报、心跳都要带它
 * @param attemptNo         第几次尝试，从 1 开始
 * @param title             任务标题（人写的那一行）
 * @param projectCode       项目标识
 * @param workdir           工作区绝对路径
 * @param branch            必须在这条分支上干活；平台已经切过去了
 * @param instruction       本次指令，末尾已经带上平台的上报约定
 * @param sessionId         平台记录的续跑钥匙（执行者报的会话 ID）；为空表示这次是新会话
 * @param images            任务附件的绝对路径，按顺序挂给模型
 * @param plugins           任务绑定的固定版本插件；内容同时已写入 instruction
 * @param environment       本次执行生效的环境信息；项目级同键值已覆盖全局值
 * @param decisionThreshold 项目级自动决策阈值：生效等级高于它就必须挂起等人
 * @param leaseSeconds      多久不心跳就会被判为失联
 * @param leaseExpiresAt    本次租约到期时间
 * @param contractUrl       协议全文与出参 schema 的地址
 */
public record AgentWorkPackage(
        String protocol,
        long taskId,
        long runId,
        int attemptNo,
        String title,
        String projectCode,
        String workdir,
        String branch,
        String instruction,
        String sessionId,
        List<Image> images,
        List<Plugin> plugins,
        List<EnvironmentVariable> environment,
        String decisionThreshold,
        int leaseSeconds,
        Instant leaseExpiresAt,
        String contractUrl) {

    public AgentWorkPackage {
        images = images == null ? List.of() : List.copyOf(images);
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
        environment = environment == null ? List.of() : List.copyOf(environment);
    }

    /** 一张任务附件。给路径不给人读的名字：执行器要的是能挂上去的绝对路径。 */
    public record Image(long id, String filename, String path) {
    }

    /** 插件版本快照。content 给外部执行器直接使用，revision 与 sha256 用于追溯。 */
    public record Plugin(long id,
                         String name,
                         int revision,
                         String filename,
                         String contentHash,
                         String content) {
    }

    /** 外部执行者需要注入自己的执行进程；值可以包含任意文本或敏感凭据。 */
    public record EnvironmentVariable(String key,
                                      String value,
                                      String scope,
                                      List<String> tags,
                                      String description) {

        public EnvironmentVariable {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }
}
