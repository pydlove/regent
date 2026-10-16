package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 项目 —— 执行链路的工作区载体：{@code path} 决定 codex 在哪里跑，
 * {@code defaultDecisionLevel} 决定多高的决策必须惊动人。
 *
 * <p>{@code ProjectProfile} 是它的只读投影（执行链路取画像用），本聚合只在「项目注册」
 * 这一个写用例里出场。改名、暂停、删除这些动作等真有入口时再加方法 —— 现在不写 setter，
 * 因为一个「什么都能改」的类，等于没有守卫。
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

    /** 尚未持久化时为 null，由仓储 insert 后通过 {@link #assignIdentity} 回填。 */
    private ProjectId id;
    private final String code;
    private final String name;
    private final String path;
    private final String techStack;
    private final String verifyMethod;
    private final DecisionLevel defaultDecisionLevel;
    private final boolean enabled;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Project(ProjectId id, String code, String name, String path, String techStack,
                    String verifyMethod, DecisionLevel defaultDecisionLevel,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.code = requireCode(code);
        this.name = requireName(name);
        this.path = requirePath(path);
        this.techStack = optionalText(techStack, MAX_TECH_STACK, "技术栈");
        this.verifyMethod = optionalText(verifyMethod, MAX_VERIFY_METHOD, "验证方式");
        this.defaultDecisionLevel = requireNotNull(defaultDecisionLevel, "项目自动决策阈值");
        this.enabled = true;
        this.createdAt = requireNotNull(createdAt, "创建时间");
        this.updatedAt = requireNotNull(updatedAt, "更新时间");
    }

    /** 注册新项目。路径只在这里做「绝对路径」这层语法校验，目录是否真的存在由应用服务查。 */
    public static Project register(String code, String name, String path,
                                   String techStack, String verifyMethod,
                                   DecisionLevel defaultDecisionLevel) {
        Instant now = Instant.now();
        return new Project(null, code, name, path, techStack, verifyMethod,
                defaultDecisionLevel, now, now);
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

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
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

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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

    private static String requirePath(String path) {
        requireText(path, "项目路径");
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
