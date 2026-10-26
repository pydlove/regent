package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.organization.domain.model.OrganizationId;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 项目 —— 业务研发主体；{@code path} 只是可选的本机工作区绑定，决定 codex 在哪里跑，
 * {@code defaultDecisionLevel} 决定多高的决策必须惊动人。
 *
 * <p>{@code ProjectProfile} 是它的只读投影（执行链路取画像用）。标识一经创建不可修改；
 * 名称、工作区路径、决策阈值与启停状态只能通过带守卫的领域方法变更。
 */
public class Project {

    /**
     * 标识会出现在分支名、日志与 URL 里，所以限制得比数据库列宽更死：
     * 小写字母开头，只含小写字母、数字、下划线。
     */
    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int MAX_NAME = 128;
    private static final int MAX_PATH = 512;
    private static final int MAX_TECH_STACK = 128;
    private static final int MAX_VERIFY_METHOD = 512;
    private static final int MAX_SUMMARY = 200_000;
    private static final int MAX_SUMMARY_ERROR = 1000;
    private static final int MAX_VERSION = 64;
    private static final Pattern VERSION_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}");

    /** 尚未持久化时为 null，由仓储 insert 后通过 {@link #assignIdentity} 回填。 */
    private ProjectId id;
    private final OrganizationId organizationId;
    private final UserId ownerId;
    private final String code;
    private String name;
    private String path;
    private final String techStack;
    private final String verifyMethod;
    private DecisionLevel defaultDecisionLevel;
    private ProjectVersionSource versionSource;
    private String fallbackVersion;
    private boolean enabled;
    private ProjectSummaryStatus summaryStatus;
    private String summary;
    private String summaryError;
    private Instant summaryGeneratedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Project(ProjectId id, OrganizationId organizationId,
                    UserId ownerId, String code, String name, String path, String techStack,
                    String verifyMethod, DecisionLevel defaultDecisionLevel,
                    ProjectVersionSource versionSource, String fallbackVersion,
                    boolean enabled, ProjectSummaryStatus summaryStatus,
                    String summary, String summaryError,
                    Instant summaryGeneratedAt,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organizationId = organizationId;
        this.ownerId = ownerId;
        this.code = requireCode(code);
        this.name = requireName(name);
        this.path = optionalPath(path);
        this.techStack = optionalText(techStack, MAX_TECH_STACK, "技术栈");
        this.verifyMethod = optionalText(verifyMethod, MAX_VERIFY_METHOD, "验证方式");
        this.defaultDecisionLevel = requireNotNull(defaultDecisionLevel, "项目自动决策阈值");
        this.versionSource = requireNotNull(versionSource, "项目版本来源");
        this.fallbackVersion = requireVersion(fallbackVersion);
        if (this.versionSource == ProjectVersionSource.MANUAL && this.fallbackVersion == null) {
            throw new DomainException("手动版本来源必须设置项目版本");
        }
        this.enabled = enabled;
        this.summaryStatus = requireNotNull(summaryStatus, "项目资料状态");
        this.summary = optionalText(summary, MAX_SUMMARY, "项目资料");
        this.summaryError = optionalText(summaryError, MAX_SUMMARY_ERROR, "项目资料失败原因");
        this.summaryGeneratedAt = summaryGeneratedAt;
        this.createdAt = requireNotNull(createdAt, "创建时间");
        this.updatedAt = requireNotNull(updatedAt, "更新时间");
    }

    /** 注册新项目。路径是可选的本机工作区绑定，目录是否真的存在由应用服务查。 */
    public static Project register(String code, String name, String path,
                                   String techStack, String verifyMethod,
                                   DecisionLevel defaultDecisionLevel) {
        return register(code, name, path, techStack, verifyMethod, defaultDecisionLevel, null);
    }

    /** 注册新项目，并记录它所属的账号。 */
    public static Project register(String code, String name, String path,
                                   String techStack, String verifyMethod,
                                   DecisionLevel defaultDecisionLevel, UserId ownerId) {
        return register(code, name, path, techStack, verifyMethod, defaultDecisionLevel, ownerId,
                ProjectVersionSource.GIT_TAG, null, null);
    }

    /** 注册新项目并配置版本来源。 */
    public static Project register(String code, String name, String path,
                                   String techStack, String verifyMethod,
                                   DecisionLevel defaultDecisionLevel, UserId ownerId,
                                   ProjectVersionSource versionSource, String fallbackVersion) {
        return register(code, name, path, techStack, verifyMethod, defaultDecisionLevel,
                ownerId, versionSource, fallbackVersion, null);
    }

    public static Project register(String code, String name, String path,
                                   String techStack, String verifyMethod,
                                   DecisionLevel defaultDecisionLevel, UserId ownerId,
                                   ProjectVersionSource versionSource, String fallbackVersion,
                                   OrganizationId organizationId) {
        Instant now = Instant.now();
        return new Project(null, organizationId, ownerId, code, name, path, techStack, verifyMethod,
                defaultDecisionLevel, versionSource, fallbackVersion,
                true, ProjectSummaryStatus.NOT_GENERATED,
                null, null, null, now, now);
    }

    /** 从数据库恢复项目。只允许基础设施的数据转换调用。 */
    public static Project restore(ProjectId id, String code, String name, String path,
                                  String techStack, String verifyMethod,
                                  DecisionLevel defaultDecisionLevel,
                                  boolean enabled,
                                  ProjectSummaryStatus summaryStatus, String summary,
                                  String summaryError, Instant summaryGeneratedAt,
                                  Instant createdAt, Instant updatedAt) {
        return restore(id, null, null, code, name, path, techStack, verifyMethod,
                defaultDecisionLevel, ProjectVersionSource.GIT_TAG, null,
                enabled, summaryStatus, summary, summaryError, summaryGeneratedAt,
                createdAt, updatedAt);
    }

    /** 从数据库恢复项目，并保留账号归属。 */
    public static Project restore(ProjectId id, UserId ownerId, String code, String name, String path,
                                  String techStack, String verifyMethod,
                                  DecisionLevel defaultDecisionLevel,
                                  boolean enabled,
                                  ProjectSummaryStatus summaryStatus, String summary,
                                  String summaryError, Instant summaryGeneratedAt,
                                  Instant createdAt, Instant updatedAt) {
        return restore(id, null, ownerId, code, name, path, techStack, verifyMethod,
                defaultDecisionLevel, ProjectVersionSource.GIT_TAG, null,
                enabled, summaryStatus, summary, summaryError, summaryGeneratedAt,
                createdAt, updatedAt);
    }

    /** 从数据库恢复项目，并保留版本策略。 */
    public static Project restore(ProjectId id, UserId ownerId, String code, String name, String path,
                                  String techStack, String verifyMethod,
                                  DecisionLevel defaultDecisionLevel,
                                  ProjectVersionSource versionSource, String fallbackVersion,
                                  boolean enabled,
                                  ProjectSummaryStatus summaryStatus, String summary,
                                  String summaryError, Instant summaryGeneratedAt,
                                  Instant createdAt, Instant updatedAt) {
        return restore(id, null, ownerId, code, name, path, techStack, verifyMethod,
                defaultDecisionLevel, versionSource, fallbackVersion,
                enabled, summaryStatus, summary, summaryError, summaryGeneratedAt,
                createdAt, updatedAt);
    }

    public static Project restore(ProjectId id, OrganizationId organizationId, UserId ownerId,
                                  String code, String name, String path,
                                  String techStack, String verifyMethod,
                                  DecisionLevel defaultDecisionLevel,
                                  ProjectVersionSource versionSource, String fallbackVersion,
                                  boolean enabled,
                                  ProjectSummaryStatus summaryStatus, String summary,
                                  String summaryError, Instant summaryGeneratedAt,
                                  Instant createdAt, Instant updatedAt) {
        return new Project(requireNotNull(id, "项目 ID"), organizationId, ownerId,
                code, name, path, techStack, verifyMethod,
                defaultDecisionLevel, versionSource, fallbackVersion,
                enabled, summaryStatus, summary, summaryError,
                summaryGeneratedAt, createdAt, updatedAt);
    }

    /** 持久化后回填数据库生成的主键。重复分配直接报错，避免两行数据悄悄并成一行。 */
    public void assignIdentity(ProjectId assigned) {
        if (this.id != null) {
            throw new DomainException("项目已有 ID，不能重复分配：" + this.id);
        }
        this.id = requireNotNull(assigned, "项目 ID");
    }

    public ProjectId getId() {
        return id;
    }

    public OrganizationId getOrganizationId() {
        return organizationId;
    }

    public UserId getOwnerId() {
        return ownerId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public boolean hasWorkspace() {
        return path != null;
    }

    /** 可空：没填就是 null，不存空串 —— 空串与「没填」在查询与展示里是两种含义。 */
    public String getTechStack() {
        return techStack;
    }

    public String getVerifyMethod() {
        return verifyMethod;
    }

    public DecisionLevel getDefaultDecisionLevel() {
        return defaultDecisionLevel;
    }

    public ProjectVersionSource getVersionSource() {
        return versionSource;
    }

    public String getFallbackVersion() {
        return fallbackVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ProjectSummaryStatus getSummaryStatus() {
        return summaryStatus;
    }

    public String getSummary() {
        return summary;
    }

    public String getSummaryError() {
        return summaryError;
    }

    public Instant getSummaryGeneratedAt() {
        return summaryGeneratedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 编辑项目画像。先完成全部校验再赋值，避免某个字段非法时对象停在半更新状态。
     */
    public void updateDetails(String name, String path, DecisionLevel defaultDecisionLevel,
                              boolean enabled) {
        updateDetails(name, path, defaultDecisionLevel, versionSource, fallbackVersion, enabled);
    }

    /** 编辑项目画像与版本策略。 */
    public void updateDetails(String name, String path, DecisionLevel defaultDecisionLevel,
                              ProjectVersionSource nextVersionSource, String nextFallbackVersion,
                              boolean enabled) {
        String nextName = requireName(name);
        String nextPath = optionalPath(path);
        DecisionLevel nextLevel = requireNotNull(defaultDecisionLevel, "项目自动决策阈值");
        ProjectVersionSource source = requireNotNull(nextVersionSource, "项目版本来源");
        String version = requireVersion(nextFallbackVersion);
        if (source == ProjectVersionSource.MANUAL && version == null) {
            throw new DomainException("手动版本来源必须设置项目版本");
        }
        this.name = nextName;
        this.path = nextPath;
        this.defaultDecisionLevel = nextLevel;
        this.versionSource = source;
        this.fallbackVersion = version;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    /** 开始一次生成。失败重试会保留上一版资料，直到新版本成功覆盖。 */
    public void startSummaryGeneration() {
        if (summaryStatus == ProjectSummaryStatus.GENERATING) {
            throw new DomainException("项目资料正在生成：" + code);
        }
        this.summaryStatus = ProjectSummaryStatus.GENERATING;
        this.summaryError = null;
        this.updatedAt = Instant.now();
    }

    public void completeSummary(String generatedSummary) {
        requireSummaryState(ProjectSummaryStatus.GENERATING, "只有生成中的项目资料可以完成");
        String normalized = optionalText(generatedSummary, MAX_SUMMARY, "项目资料");
        if (normalized == null) {
            throw new DomainException("生成的项目资料不能为空：" + code);
        }
        this.summary = normalized;
        this.summaryError = null;
        this.summaryStatus = ProjectSummaryStatus.READY;
        this.summaryGeneratedAt = Instant.now();
        this.updatedAt = this.summaryGeneratedAt;
    }

    public void failSummary(String reason) {
        requireSummaryState(ProjectSummaryStatus.GENERATING, "只有生成中的项目资料可以标记失败");
        String normalized = reason == null || reason.isBlank() ? null : reason.trim();
        if (normalized != null && normalized.length() > MAX_SUMMARY_ERROR) {
            normalized = normalized.substring(0, MAX_SUMMARY_ERROR);
        }
        this.summaryStatus = ProjectSummaryStatus.FAILED;
        this.summaryError = normalized == null ? "项目资料生成失败，未返回具体原因" : normalized;
        this.updatedAt = Instant.now();
    }

    private static String requireCode(String code) {
        requireText(code, "项目标识");
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new DomainException("项目标识只能是小写字母开头的字母、数字、下划线：" + code);
        }
        return code;
    }

    private static String requireName(String name) {
        requireText(name, "项目名称");
        if (name.length() > MAX_NAME) {
            throw new DomainException("项目名称超长（上限 " + MAX_NAME + " 字符）：" + name.length());
        }
        return name;
    }

    private static String optionalPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        path = path.strip();
        if (path.length() > MAX_PATH) {
            throw new DomainException("项目路径超长（上限 " + MAX_PATH + " 字符）：" + path.length());
        }
        if (!path.startsWith("/")) {
            throw new DomainException("项目路径必须是绝对路径：" + path);
        }
        return path;
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new DomainException(field + "超长（上限 " + max + " 字符）：" + trimmed.length());
        }
        return trimmed;
    }

    private static String requireVersion(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_VERSION || !VERSION_PATTERN.matcher(normalized).matches()) {
            throw new DomainException("项目版本只能包含字母、数字、点、下划线、加号和连字符，且不能超过 "
                    + MAX_VERSION + " 个字符");
        }
        return normalized;
    }

    private void requireSummaryState(ProjectSummaryStatus expected, String message) {
        if (summaryStatus != expected) {
            throw new DomainException(message + "：" + code + "（当前 " + summaryStatus + "）");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(field + "不能为空");
        }
    }

    /** 与 Task 保持同一种失败方式：领域守卫一律抛 {@link DomainException}，不抛空指针。 */
    private static <T> T requireNotNull(T value, String field) {
        if (value == null) {
            throw new DomainException(field + "不能为空");
        }
        return value;
    }
}
