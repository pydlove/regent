package com.pydlove.regent.project.domain.model;

import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentEntryTest {

    @Test
    @DisplayName("全局与项目级归属互斥且完整")
    void validatesScopeOwner() {
        EnvironmentEntry global = EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "DB_HOST", "127.0.0.1", List.of(), "本地数据库");

        assertNull(global.getProjectId());
        assertEquals(EnvironmentScope.GLOBAL, global.getScope());
        assertThrows(DomainException.class, () -> EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, ProjectId.of(1L), "DB_HOST", "x", List.of(), "数据库"));
        assertThrows(DomainException.class, () -> EnvironmentEntry.create(
                EnvironmentScope.PROJECT, null, "DB_HOST", "x", List.of(), "数据库"));
    }

    @Test
    @DisplayName("环境键必须是可注入进程环境的稳定标识")
    void validatesKey() {
        assertThrows(DomainException.class, () -> EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "db.host", "x", List.of(), "数据库"));
        assertThrows(DomainException.class, () -> EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "1DB_HOST", "x", List.of(), "数据库"));
        assertThrows(DomainException.class, () -> EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, " ", "x", List.of(), "数据库"));
    }

    @Test
    @DisplayName("环境值可以为空或任意多行文本")
    void acceptsArbitraryValue() {
        EnvironmentEntry entry = EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "CERT", "line-1\nline-2", List.of(), "证书");

        assertEquals("line-1\nline-2", entry.getValue());
        assertEquals("", EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "EMPTY", null, List.of(), "空值").getValue());
    }

    @Test
    @DisplayName("标签去空白、去重并保持输入顺序")
    void normalizesTags() {
        EnvironmentEntry entry = EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "DB_HOST", "x",
                List.of(" 数据库 ", "基础设施", "数据库", ""), "数据库地址");

        assertEquals(List.of("数据库", "基础设施"), entry.getTags());
        assertThrows(DomainException.class, () -> EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "DB_HOST", "x", List.of("db,prod"), "数据库地址"));
    }

    @Test
    @DisplayName("更新可以改变归属和键值并刷新更新时间")
    void updatesFields() {
        EnvironmentEntry entry = EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "DB_HOST", "old", List.of("old"), "旧值");

        entry.update(EnvironmentScope.PROJECT, ProjectId.of(7L),
                "DB_URL", "jdbc:mysql://db/app", List.of("数据库"), "项目数据库", true);

        assertEquals(EnvironmentScope.PROJECT, entry.getScope());
        assertEquals(ProjectId.of(7L), entry.getProjectId());
        assertEquals("DB_URL", entry.getKey());
        assertEquals("jdbc:mysql://db/app", entry.getValue());
        assertEquals(List.of("数据库"), entry.getTags());
        assertEquals("项目数据库", entry.getDescription());
        assertTrue(entry.isSecret());
        assertTrue(!entry.getUpdatedAt().isBefore(entry.getCreatedAt()));
    }

    @Test
    @DisplayName("名称必填且最多 64 个字符")
    void validatesNameLength() {
        assertThrows(DomainException.class, () -> EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "DB_HOST", "x", List.of(), " "));
        assertThrows(DomainException.class, () -> EnvironmentEntry.create(
                EnvironmentScope.GLOBAL, null, "DB_HOST", "x", List.of(), "名".repeat(65)));
    }
}
