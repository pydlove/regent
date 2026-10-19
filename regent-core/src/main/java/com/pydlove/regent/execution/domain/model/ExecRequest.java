package com.pydlove.regent.execution.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 一次执行请求。
 *
 * @param projectId      所属项目，仅用于日志与事件归属，不参与执行
 * @param projectPath    工作区绝对路径，执行器在这里起进程
 * @param prompt         本次要交给执行器的指令；续跑时是决策结论而不是原始需求
 * @param resumeThreadId 断点续跑的会话 ID；为空表示新会话
 * @param timeout        单次执行的墙钟上限，超时由适配器杀进程并回报 TIMEOUT
 * @param imagePaths     随本次指令一起交给模型的图片绝对路径（任务附件）。
 *                       是路径不是字节：图片由平台落在自己的存储目录里，执行器只需按路径挂上去，
 *                       把几兆的字节在领域对象里搬来搬去没有任何意义
 * @param environmentVariables 注入执行器进程的环境变量；同名项目值已在应用层覆盖全局值
 * @param branchName 平台已批准并切换到的任务分支；为空表示调用方没有提供分支约束
 * @param accessMode 执行器对工作区的访问级别；项目资料分析等只读任务不得获得写权限
 */
public record ExecRequest(
        ProjectId projectId,
        String projectPath,
        String prompt,
        String resumeThreadId,
        Duration timeout,
        List<String> imagePaths,
        Map<String, String> environmentVariables,
        String branchName,
        AgentAccessMode accessMode) {

    /** 不附加环境变量的兼容构造器，现有调用方可继续只描述一次基础执行。 */
    public ExecRequest(ProjectId projectId,
                       String projectPath,
                       String prompt,
                       String resumeThreadId,
                       Duration timeout,
                       List<String> imagePaths) {
        this(projectId, projectPath, prompt, resumeThreadId, timeout, imagePaths, Map.of());
    }

    /** 不附加分支约束的兼容构造器。生产执行路径应由 TaskRunService 传入真实分支。 */
    public ExecRequest(ProjectId projectId,
                       String projectPath,
                       String prompt,
                       String resumeThreadId,
                       Duration timeout,
                       List<String> imagePaths,
                       Map<String, String> environmentVariables) {
        this(projectId, projectPath, prompt, resumeThreadId, timeout, imagePaths,
                environmentVariables, null, AgentAccessMode.WORKSPACE_WRITE);
    }

    /** 兼容不指定访问级别的任务执行；默认仍允许写工作区。 */
    public ExecRequest(ProjectId projectId,
                       String projectPath,
                       String prompt,
                       String resumeThreadId,
                       Duration timeout,
                       List<String> imagePaths,
                       Map<String, String> environmentVariables,
                       String branchName) {
        this(projectId, projectPath, prompt, resumeThreadId, timeout, imagePaths,
                environmentVariables, branchName, AgentAccessMode.WORKSPACE_WRITE);
    }

    public ExecRequest {
        Objects.requireNonNull(projectId, "所属项目不能为空");
        if (projectPath == null || projectPath.isBlank()) {
            throw new DomainException("项目路径不能为空");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new DomainException("执行指令不能为空");
        }
        Objects.requireNonNull(timeout, "超时时间不能为空");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new DomainException("超时时间必须为正：" + timeout);
        }
        imagePaths = imagePaths == null ? List.of() : List.copyOf(imagePaths);
        environmentVariables = environmentVariables == null ? Map.of() : Map.copyOf(environmentVariables);
        branchName = (branchName == null || branchName.isBlank()) ? null : branchName.trim();
        accessMode = accessMode == null ? AgentAccessMode.WORKSPACE_WRITE : accessMode;
    }

    public boolean hasImages() {
        return !imagePaths.isEmpty();
    }

    /** 是续跑而不是新会话。 */
    public boolean isResume() {
        return resumeThreadId != null && !resumeThreadId.isBlank();
    }

    public boolean isReadOnly() {
        return accessMode == AgentAccessMode.READ_ONLY;
    }
}
