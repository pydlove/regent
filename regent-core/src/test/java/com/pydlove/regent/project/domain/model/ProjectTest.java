package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 项目聚合的守卫测试。这里守的是「注册进来的项目能不能被放心当作工作区」：
 * 标识要能安全地进分支名与 URL，路径要能直接交给 {@code codex exec -C}。
 */
class ProjectTest {

    private static Project register(String code, String name, String path) {
        return Project.register(code, name, path, "Java 17 / Maven", "mvn -q test", DecisionLevel.L1);
    }

    @Test
    @DisplayName("注册成功后是启用状态，ID 等持久化回填")
    void registersEnabledWithoutIdentity() {
        Project project = register("app_audit", "审计平台", "/tmp/regent-app-audit");

        assertNull(project.getId(), "尚未落库就不该有 ID");
        assertTrue(project.isEnabled(), "新注册的项目默认启用");
        assertEquals(DecisionLevel.L1, project.getDefaultDecisionLevel());
        assertEquals("Java 17 / Maven", project.getTechStack());
        assertEquals(ProjectSummaryStatus.NOT_GENERATED, project.getSummaryStatus());
    }

    @Test
    @DisplayName("技术栈与验证方式留空时归 null，不存空串")
    void blankOptionalFieldsBecomeNull() {
        Project project = Project.register("app_audit", "审计平台", "/tmp/x", "  ", "", DecisionLevel.L1);

        assertNull(project.getTechStack());
        assertNull(project.getVerifyMethod());
    }

    @Test
    @DisplayName("标识只允许小写字母开头的字母数字下划线")
    void rejectsIllegalCode() {
        assertThrows(DomainException.class, () -> register("App_Audit", "审计平台", "/tmp/x"));
        assertThrows(DomainException.class, () -> register("app-audit", "审计平台", "/tmp/x"));
        assertThrows(DomainException.class, () -> register("2app", "审计平台", "/tmp/x"));
        assertThrows(DomainException.class, () -> register("", "审计平台", "/tmp/x"));
    }

    @Test
    @DisplayName("名称不能为空，路径必须是绝对路径")
    void rejectsBlankNameAndRelativePath() {
        assertThrows(DomainException.class, () -> register("app_audit", " ", "/tmp/x"));
        assertThrows(DomainException.class, () -> register("app_audit", "审计平台", "relative/path"));
    }

    @Test
    @DisplayName("ID 只能分配一次，重复分配直接报错")
    void identityAssignedOnce() {
        Project project = register("app_audit", "审计平台", "/tmp/x");

        project.assignIdentity(ProjectId.of(7L));
        assertEquals(ProjectId.of(7L), project.getId());
        assertThrows(DomainException.class, () -> project.assignIdentity(ProjectId.of(8L)));
        assertEquals(ProjectId.of(7L), project.getId(), "重复分配不该改掉已有 ID");
    }

    @Test
    @DisplayName("编辑只改允许的画像字段，项目标识保持不变")
    void updatesEditableProfileFields() {
        Project project = register("app_audit", "审计平台", "/tmp/old");

        project.updateDetails("安全审计平台", "/tmp/new", DecisionLevel.L3, false);

        assertEquals("app_audit", project.getCode());
        assertEquals("安全审计平台", project.getName());
        assertEquals("/tmp/new", project.getPath());
        assertEquals(DecisionLevel.L3, project.getDefaultDecisionLevel());
        assertFalse(project.isEnabled());
    }

    @Test
    @DisplayName("编辑校验失败时不留下半更新状态")
    void rejectsInvalidEditWithoutPartialMutation() {
        Project project = register("app_audit", "审计平台", "/tmp/old");

        assertThrows(DomainException.class,
                () -> project.updateDetails("新名称", "relative/path", DecisionLevel.L3, false));
        assertThrows(DomainException.class,
                () -> project.updateDetails(" ", "/tmp/new", DecisionLevel.L3, false));

        assertEquals("审计平台", project.getName());
        assertEquals("/tmp/old", project.getPath());
        assertEquals(DecisionLevel.L1, project.getDefaultDecisionLevel());
        assertTrue(project.isEnabled());
    }

    @Test
    @DisplayName("从数据库恢复时保留启用状态")
    void restoresEnabledFlag() {
        Project project = Project.restore(
                ProjectId.of(9L), "app_audit", "审计平台", "/tmp/x",
                null, null, DecisionLevel.L2, false,
                ProjectSummaryStatus.NOT_GENERATED, null, null, null,
                java.time.Instant.parse("2026-10-01T00:00:00Z"),
                java.time.Instant.parse("2026-10-02T00:00:00Z"));

        assertFalse(project.isEnabled());
    }

    @Test
    @DisplayName("项目资料只能按生成中 → 可用推进，并记录生成时间")
    void completesSummaryFromGeneratingState() {
        Project project = register("app_audit", "审计平台", "/tmp/x");

        project.startSummaryGeneration();
        assertEquals(ProjectSummaryStatus.GENERATING, project.getSummaryStatus());
        project.completeSummary("# 项目资料\n\nJava 17 / Maven");

        assertEquals(ProjectSummaryStatus.READY, project.getSummaryStatus());
        assertTrue(project.getSummary().contains("Java 17"));
        assertNull(project.getSummaryError());
        assertTrue(project.getSummaryGeneratedAt() != null);
        assertThrows(DomainException.class, () -> project.failSummary("迟到的失败"));
    }

    @Test
    @DisplayName("生成失败保留上一版资料，并给出可重试状态")
    void keepsPreviousSummaryWhenRegenerationFails() {
        Project project = register("app_audit", "审计平台", "/tmp/x");
        project.startSummaryGeneration();
        project.completeSummary("上一版");

        project.startSummaryGeneration();
        project.failSummary("Codex 超时");

        assertEquals(ProjectSummaryStatus.FAILED, project.getSummaryStatus());
        assertEquals("上一版", project.getSummary(), "失败不应抹掉已知可用的旧资料");
        assertEquals("Codex 超时", project.getSummaryError());
    }

    @Test
    @DisplayName("未进入生成中时不能写入结果")
    void rejectsSummaryCompletionWithoutGeneration() {
        Project project = register("app_audit", "审计平台", "/tmp/x");

        assertThrows(DomainException.class, () -> project.completeSummary("凭空出现"));
        assertThrows(DomainException.class, () -> project.failSummary("凭空失败"));
    }

    @Nested
    @DisplayName("决策阈值")
    class Threshold {

        @Test
        @DisplayName("阈值不能为空 —— 没它就等于没有升级规则")
        void rejectsNullLevel() {
            assertThrows(DomainException.class,
                    () -> Project.register("app_audit", "审计平台", "/tmp/x", null, null, null));
        }

        @Test
        @DisplayName("阈值原样保留，L3 也不会被夹到 L1")
        void keepsLevelAsGiven() {
            Project project = Project.register("app_audit", "审计平台", "/tmp/x", null, null, DecisionLevel.L3);

            assertEquals(DecisionLevel.L3, project.getDefaultDecisionLevel());
            assertFalse(project.getDefaultDecisionLevel().isWithin(DecisionLevel.L1));
        }
    }
}
