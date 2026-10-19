package com.pydlove.regent.project.domain.repository;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.time.Instant;
import java.util.Objects;

/**
 * 项目画像的只读投影 —— 执行一个任务需要知道的那几件事。
 *
 * <p>为什么不建 {@code Project} 聚合：目前没有任何用例会「取项目来改」，
 * 而执行链路要的只是「路径在哪、阈值多少、还允许派发吗」。为一个只读用途造一个聚合根，
 * 换来的是一堆没有守卫可言的 getter，那正是贫血模型。等到项目注册与画像编辑落地时，
 * 再把它升级成聚合根，届时这个投影会退化成它的一个查询视图。
 *
 * @param id                   项目 ID
 * @param code                 项目唯一标识
 * @param name                 展示名称
 * @param path                 工作区绝对路径
 * @param defaultDecisionLevel 项目级自动决策阈值；生效等级高于它就必须由人拍板
 * @param enabled              暂停开关；关掉之后不再派发新任务
 * @param summaryStatus        Codex 项目资料的生成状态
 * @param summary              Codex 生成的项目资料；尚未生成或首次生成失败时为空
 * @param summaryError         最近一次生成失败原因
 * @param summaryGeneratedAt   最近一次成功生成时间
 */
public record ProjectProfile(
        ProjectId id,
        String code,
        String name,
        String path,
        DecisionLevel defaultDecisionLevel,
        boolean enabled,
        ProjectSummaryStatus summaryStatus,
        String summary,
        String summaryError,
        Instant summaryGeneratedAt) {

    /** 兼容只关心执行所需字段的调用方。 */
    public ProjectProfile(ProjectId id, String code, String name, String path,
                          DecisionLevel defaultDecisionLevel, boolean enabled) {
        this(id, code, name, path, defaultDecisionLevel, enabled,
                ProjectSummaryStatus.NOT_GENERATED, null, null, null);
    }

    public ProjectProfile {
        Objects.requireNonNull(id, "项目 ID 不能为空");
        if (code == null || code.isBlank()) {
            throw new DomainException("项目标识不能为空");
        }
        if (path == null || path.isBlank()) {
            throw new DomainException("项目路径不能为空：" + code);
        }
        Objects.requireNonNull(defaultDecisionLevel, "项目自动决策阈值不能为空");
        summaryStatus = summaryStatus == null ? ProjectSummaryStatus.NOT_GENERATED : summaryStatus;
        summary = summary == null || summary.isBlank() ? null : summary;
        summaryError = summaryError == null || summaryError.isBlank() ? null : summaryError;
    }

    /** 派发前的最后一道闸：项目被暂停时，任何新任务都不许起跑。 */
    public void requireDispatchable() {
        if (!enabled) {
            throw new DomainException("项目已暂停派发：" + code + "（" + name + "）");
        }
    }
}
