package com.pydlove.regent.testing.infrastructure.persistence;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.testing.domain.model.TestCase;
import com.pydlove.regent.testing.domain.model.TestCaseBinding;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRun;
import com.pydlove.regent.testing.domain.model.TestCaseRunId;
import com.pydlove.regent.testing.domain.model.TestCaseRunStatus;
import com.pydlove.regent.testing.domain.model.TestCaseRunTrigger;
import com.pydlove.regent.testing.domain.model.TestCaseType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 测试用例领域对象与 MyBatis 数据对象转换。 */
public final class TestCaseDataMapper {

    private TestCaseDataMapper() {
    }

    public static TestCaseData toData(TestCase testCase) {
        TestCaseData data = new TestCaseData();
        data.setId(testCase.getId() == null ? null : testCase.getId().value());
        data.setProjectId(testCase.getProjectId().value());
        data.setName(testCase.getName());
        data.setTestType(testCase.getType().name());
        data.setDescription(testCase.getDescription());
        data.setCommandText(testCase.getCommand());
        data.setScriptFilename(testCase.getScriptFilename());
        data.setScriptContent(testCase.getScriptContent());
        data.setScriptHash(testCase.getScriptHash());
        data.setEnabled(testCase.isEnabled());
        data.setCreatedAt(toDb(testCase.getCreatedAt()));
        data.setUpdatedAt(toDb(testCase.getUpdatedAt()));
        return data;
    }

    public static TestCase toDomain(TestCaseRow row) {
        return TestCase.restore(
                TestCaseId.of(row.getId()),
                ProjectId.of(row.getProjectId()),
                row.getName(),
                TestCaseType.parse(row.getTestType()),
                row.getDescription(),
                row.getCommandText(),
                row.getScriptFilename(),
                row.getScriptContent(),
                row.getScriptHash(),
                Boolean.TRUE.equals(row.getEnabled()),
                toInstant(row.getCreatedAt()),
                toInstant(row.getUpdatedAt()));
    }

    public static TestCaseBinding toBinding(TestCaseBindingRow row) {
        return new TestCaseBinding(
                TestCaseId.of(row.getTestCaseId()),
                row.getName(),
                TestCaseType.parse(row.getTestType()),
                row.getCommandText(),
                row.getScriptFilename(),
                row.getScriptContent(),
                row.getScriptHash(),
                Boolean.TRUE.equals(row.getEnabled()),
                Boolean.TRUE.equals(row.getDeleted()),
                toInstant(row.getBoundAt()));
    }

    public static TestCaseRunData toData(TestCaseRun run) {
        TestCaseRunData data = new TestCaseRunData();
        data.setId(run.getId() == null ? null : run.getId().value());
        data.setProjectId(run.getProjectId().value());
        data.setTaskId(run.getTaskId().map(TaskId::value).orElse(null));
        data.setTriggerType(run.getTrigger().name());
        data.setTestCaseId(run.getTestCaseId().value());
        data.setTestCaseName(run.getTestCaseName());
        data.setTestType(run.getType().name());
        data.setCommandText(run.getCommand());
        data.setScriptFilename(run.getScriptFilename());
        data.setScriptContent(run.getScriptContent());
        data.setScriptHash(run.getScriptHash());
        data.setStatus(run.getStatus().name());
        data.setExitCode(run.getExitCode());
        data.setSummary(run.getSummary());
        data.setOutput(run.getOutput());
        data.setQueuedAt(toDb(run.getQueuedAt()));
        data.setStartedAt(toDb(run.getStartedAt()));
        data.setFinishedAt(toDb(run.getFinishedAt()));
        data.setCreatedAt(toDb(run.getCreatedAt()));
        return data;
    }

    public static TestCaseRun toDomain(TestCaseRunRow row) {
        return TestCaseRun.restore(
                TestCaseRunId.of(row.getId()),
                ProjectId.of(row.getProjectId()),
                row.getTaskId() == null ? null : TaskId.of(row.getTaskId()),
                TestCaseRunTrigger.parse(row.getTriggerType()),
                TestCaseId.of(row.getTestCaseId()),
                row.getTestCaseName(),
                TestCaseType.parse(row.getTestType()),
                row.getCommandText(),
                row.getScriptFilename(),
                row.getScriptContent(),
                row.getScriptHash(),
                TestCaseRunStatus.parse(row.getStatus()),
                row.getExitCode(),
                row.getSummary(),
                row.getOutput(),
                toInstant(row.getQueuedAt()),
                toInstant(row.getStartedAt()),
                toInstant(row.getFinishedAt()),
                toInstant(row.getCreatedAt()));
    }

    private static LocalDateTime toDb(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
