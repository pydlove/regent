package com.pydlove.regent.testing.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 测试用例与任务绑定映射接口。 */
public interface TestCaseMapper {

    TestCaseRow selectById(@Param("id") long id);

    TestCaseRow selectByIdForUpdate(@Param("id") long id);

    List<TestCaseRow> selectAll();

    List<TestCaseRow> selectByProject(@Param("projectId") long projectId);

    int countByName(@Param("projectId") long projectId,
                    @Param("name") String name,
                    @Param("excludingId") Long excludingId);

    int insert(TestCaseData data);

    int update(TestCaseData data);

    int softDelete(@Param("id") long id,
                   @Param("updatedAt") LocalDateTime updatedAt);

    List<TestCaseBindingRow> selectBindingsByTask(@Param("taskId") long taskId);

    int deleteBindingsByTask(@Param("taskId") long taskId);

    int insertBinding(@Param("taskId") long taskId,
                      @Param("testCaseId") long testCaseId,
                      @Param("boundAt") LocalDateTime boundAt);
}
