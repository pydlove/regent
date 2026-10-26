package com.pydlove.regent.project.domain.repository;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.model.ProjectSummaryStatus;
import com.pydlove.regent.project.domain.model.ProjectVersionSource;
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
 * @param ownerId              项目归属账号
 * @param code                 项目唯一标识
 * @param name                 展示名称
 * @param path                 可选的本机工作区绝对路径
 * @param defaultDecisionLevel 项目级自动决策阈值；生效等级高于它就必须由人拍板
 * @param enabled              暂停开关；关掉之后不再派发新任务
 * @param summaryStatus        Codex 项目资料的生成状态
 * @param summary              Codex 生成的项目资料；尚未生成或首次生成失败时为空
 * @param summaryError         最近一次生成失败原因
 * @param summaryGeneratedAt   最近一次成功生成时间
 * @param versionSource        项目版本来源
 * @param fallbackVersion      Git Tag 不可用或手动模式使用的版本
 */
public record ProjectProfile(
        ProjectId id,
        UserId ownerId,
        String code,
        String name,
        String path,
        DecisionLevel defaultDecisionLevel,
        boolean enabled,
        ProjectSummaryStatus summaryStatus,
        String summary,
        String summaryError,
        Instant summaryGeneratedAt,
        ProjectVersionSource versionSource,
        String fallbackVersion,
        OrganizationId organizationId) {

    /** 兼容只关心执行所需字段的调用方。 */
    public ProjectProfile(ProjectId id, String code, String name, String path,
                          DecisionLevel defaultDecisionLevel, boolean enabled) {
        this(id, null, code, name, path, defaultDecisionLevel, enabled,
                ProjectSummaryStatus.NOT_GENERATED, null, null, null,
                ProjectVersionSource.GIT_TAG, null, null);
    }

    /** 兼容旧调用方：不带归属时按无主画像处理。 */
    public ProjectProfile(ProjectId id, String code, String name, String path,
                          DecisionLevel defaultDecisionLevel, boolean enabled,
                          ProjectSummaryStatus summaryStatus, String summary,
                          String summaryError, Instant summaryGeneratedAt) {
        this(id, null, code, name, path, defaultDecisionLevel, enabled,
                summaryStatus, summary, summaryError, summaryGeneratedAt,
                ProjectVersionSource.GIT_TAG, null, null);
    }

    /** 兼容未接入版本策略的调用方。 */
    public ProjectProfile(ProjectId id, UserId ownerId, String code, String name, String path,
                          DecisionLevel defaultDecisionLevel, boolean enabled,
                          ProjectSummaryStatus summaryStatus, String summary,
                          String summaryError, Instant summaryGeneratedAt) {
        this(id, ownerId, code, name, path, defaultDecisionLevel, enabled,
                summaryStatus, summary, summaryError, summaryGeneratedAt,
                ProjectVersionSource.GIT_TAG, null, null);
    }

    public ProjectProfile(ProjectId id, UserId ownerId, String code, String name, String path,
                          DecisionLevel defaultDecisionLevel, boolean enabled,
                          ProjectSummaryStatus summaryStatus, String summary,
                          String summaryError, Instant summaryGeneratedAt,
                          ProjectVersionSource versionSource, String fallbackVersion) {
        this(id, ownerId, code, name, path, defaultDecisionLevel, enabled,
                summaryStatus, summary, summaryError, summaryGeneratedAt,
                versionSource, fallbackVersion, null);
    }

    public ProjectProfile {
        Objects.requireNonNull(id, "项目 ID 不能为空");
        if (code == null || code.isBlank()) {
            throw new DomainException("项目标识不能为空");
        }
        path = path == null || path.isBlank() ? null : path.strip();
        if (path != null && !path.startsWith("/")) {
            throw new DomainException("项目路径必须是绝对路径：" + path);
        }
        Objects.requireNonNull(defaultDecisionLevel, "项目自动决策阈值不能为空");
        summaryStatus = summaryStatus == null ? ProjectSummaryStatus.NOT_GENERATED : summaryStatus;
        summary = summary == null || summary.isBlank() ? null : summary;
        summaryError = summaryError == null || summaryError.isBlank() ? null : summaryError;
        versionSource = versionSource == null ? ProjectVersionSource.GIT_TAG : versionSource;
        fallbackVersion = fallbackVersion == null || fallbackVersion.isBlank()
                ? null : fallbackVersion.strip();
        if (versionSource == ProjectVersionSource.MANUAL && fallbackVersion == null) {
            throw new DomainException("手动版本来源必须设置项目版本");
        }
    }

    /** 派发前的最后一道闸：项目被暂停时，任何新任务都不许起跑。 */
    public void requireDispatchable() {
        if (!enabled) {
            throw new DomainException("项目已暂停派发：" + code + "（" + name + "）");
        }
        if (path == null) {
            throw new DomainException("项目尚未绑定本机代码目录，请由开发成员完成工作区配置后再派发");
        }
    }
}
