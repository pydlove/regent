package com.pydlove.regent.testing.infrastructure.persistence;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 测试执行记录映射接口。 */
public interface TestCaseRunMapper {

    TestCaseRunRow selectById(@Param("id") long id);

    List<TestCaseRunRow> selectByTask(@Param("taskId") long taskId);

    List<TestCaseRunRow> selectByTestCase(@Param("testCaseId") long testCaseId,
                                          @Param("limit") int limit);

    List<TestCaseRunRow> selectLatestByTestCaseIds(@Param("testCaseIds") List<Long> testCaseIds);

    List<TestCaseRunRow> selectActive();

    int countByTask(@Param("taskId") long taskId);

    int insert(TestCaseRunData data);

    int update(TestCaseRunData data);
}
