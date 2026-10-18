package com.pydlove.regent.testing.infrastructure.process;

import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.testing.domain.model.TestCaseExecutionRequest;
import com.pydlove.regent.testing.domain.model.TestCaseExecutionResult;
import com.pydlove.regent.testing.domain.model.TestCaseId;
import com.pydlove.regent.testing.domain.model.TestCaseRunStatus;
import com.pydlove.regent.testing.domain.model.TestCaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessTestCaseExecutorTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("上传脚本会还原到临时目录并拿到任务环境变量")
    void runsUploadedScript() throws Exception {
        Path project = Files.createDirectories(temp.resolve("project"));
        ProcessTestCaseExecutor executor = new ProcessTestCaseExecutor(
                temp.resolve("runs"), Duration.ofSeconds(5), 8_192);

        TestCaseExecutionResult result = executor.execute(new TestCaseExecutionRequest(
                TaskId.of(12L),
                TestCaseId.of(3L),
                TestCaseType.WEB,
                null,
                "check.sh",
                "echo \"task=$REGENT_TASK_ID case=$REGENT_TEST_CASE_ID\"\n",
                project.toString(),
                Map.of("API_BASE", "http://127.0.0.1:8080")));

        assertEquals(TestCaseRunStatus.SUCCESS, result.status());
        assertEquals(0, result.exitCode());
        assertTrue(result.output().contains("task=12 case=3"));
    }

    @Test
    @DisplayName("非零退出码判失败并保留输出")
    void reportsFailure() throws Exception {
        Path project = Files.createDirectories(temp.resolve("project-fail"));
        ProcessTestCaseExecutor executor = new ProcessTestCaseExecutor(
                temp.resolve("runs"), Duration.ofSeconds(5), 8_192);

        TestCaseExecutionResult result = executor.execute(new TestCaseExecutionRequest(
                TaskId.of(12L),
                TestCaseId.of(4L),
                TestCaseType.API,
                "echo 'assertion failed'; exit 3",
                null,
                null,
                project.toString(),
                Map.of()));

        assertEquals(TestCaseRunStatus.FAILED, result.status());
        assertEquals(3, result.exitCode());
        assertTrue(result.output().contains("assertion failed"));
    }

    @Test
    @DisplayName("超过墙钟上限会终止进程并标记超时")
    void reportsTimeout() throws Exception {
        Path project = Files.createDirectories(temp.resolve("project-timeout"));
        ProcessTestCaseExecutor executor = new ProcessTestCaseExecutor(
                temp.resolve("runs"), Duration.ofMillis(150), 8_192);

        TestCaseExecutionResult result = executor.execute(new TestCaseExecutionRequest(
                TaskId.of(12L),
                TestCaseId.of(5L),
                TestCaseType.API,
                "sleep 2",
                null,
                null,
                project.toString(),
                Map.of()));

        assertEquals(TestCaseRunStatus.TIMEOUT, result.status());
    }
}
