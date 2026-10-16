package com.pydlove.regent.execution.domain.repository;

import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 查询条件规范化测试。
 *
 * <p>这些边界不写测试就会在真实使用中暴露：页码被手工改成 0 是常态，
 * 而 {@code LIMIT -20} 在 MySQL 里直接是语法错误。
 */
class TaskQueryTest {

    @Test
    @DisplayName("页码与每页条数被夹到合法区间")
    void 规范化分页参数() {
        assertEquals(1, TaskQuery.of(null, null, null, 0, 20).page());
        assertEquals(1, TaskQuery.of(null, null, null, -5, 20).page());
        assertEquals(1, TaskQuery.of(null, null, null, 1, 0).size());
        assertEquals(200, TaskQuery.of(null, null, null, 1, 9999).size());
        assertEquals(20, TaskQuery.of(null, null, null, null, null).size());
    }

    @Test
    @DisplayName("关键词去空白，纯空白视为不筛选")
    void 规范化关键词() {
        assertEquals("登录", TaskQuery.of(null, null, "  登录  ", 1, 20).keyword());
        assertNull(TaskQuery.of(null, null, "   ", 1, 20).keyword());
    }

    @Test
    @DisplayName("状态缺省不筛选，拼错则拒绝")
    void 解析状态() {
        assertNull(TaskQuery.of("", null, null, 1, 20).status());
        assertNull(TaskQuery.of(null, null, null, 1, 20).status());
        assertEquals(TaskStatus.FAILED, TaskQuery.of("failed", null, null, 1, 20).status());
        assertThrows(DomainException.class, () -> TaskQuery.of("FAIL", null, null, 1, 20));
    }

    @Test
    @DisplayName("项目筛选：缺省不筛，非法 ID 直接拒绝")
    void 解析项目筛选() {
        assertNull(TaskQuery.of(null, null, null, 1, 20).projectId());
        assertEquals(ProjectId.of(7L), TaskQuery.of(null, 7L, null, 1, 20).projectId());
        assertThrows(DomainException.class, () -> TaskQuery.of(null, 0L, null, 1, 20));
        assertThrows(DomainException.class, () -> TaskQuery.of(null, -1L, null, 1, 20));
    }

    @Test
    @DisplayName("offset 由页码推出，供 SQL 的 LIMIT 使用")
    void 计算偏移量() {
        assertEquals(0, TaskQuery.of(null, null, null, 1, 20).offset());
        assertEquals(40, TaskQuery.of(null, null, null, 3, 20).offset());
    }
}
