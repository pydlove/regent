package com.pydlove.regent.bug.domain.repository;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 工作台项目筛选在缺陷读侧必须落成强类型条件。 */
class BugQueryTest {

    @Test
    @DisplayName("项目 ID 从接口参数进入缺陷查询")
    void carriesProjectFilter() {
        BugQuery query = BugQuery.of("MAJOR", "OPEN", 9L, null, 1, 20);

        assertEquals(ProjectId.of(9L), query.projectId());
    }

    @Test
    @DisplayName("非法项目 ID 直接拒绝")
    void rejectsInvalidProjectId() {
        assertThrows(DomainException.class,
                () -> BugQuery.of(null, "OPEN", 0L, null, 1, 20));
    }
}
