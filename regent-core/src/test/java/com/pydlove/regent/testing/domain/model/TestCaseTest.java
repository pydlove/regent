package com.pydlove.regent.testing.domain.model;

import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCaseTest {

    private static final ProjectId PROJECT = ProjectId.of(1L);

    @Test
    @DisplayName("命令型用例无需上传脚本")
    void createsCommandOnlyCase() {
        TestCase testCase = TestCase.create(PROJECT, "接口冒烟", TestCaseType.API,
                "检查健康接口", "curl -fsS http://127.0.0.1:8787/actuator/health", null, null, true);

        assertTrue(testCase.isEnabled());
        assertEquals(TestCaseType.API, testCase.getType());
        assertFalse(testCase.hasScript());
    }

    @Test
    @DisplayName("上传脚本会计算内容摘要，并允许不填命令")
    void createsUploadedScriptCase() {
        TestCase testCase = TestCase.create(PROJECT, "登录页回归", TestCaseType.WEB,
                null, null, "login.sh", "echo ok\n", true);

        assertTrue(testCase.hasScript());
        assertEquals(64, testCase.getScriptHash().length());
        assertEquals("login.sh", testCase.getScriptFilename());
    }

    @Test
    @DisplayName("既没有命令也没有脚本时拒绝创建")
    void rejectsEmptyExecution() {
        assertThrows(DomainException.class, () -> TestCase.create(
                PROJECT, "空用例", TestCaseType.WEB, null, null, null, null, true));
    }

    @Test
    @DisplayName("脚本文件名不能带路径")
    void rejectsPathInFilename() {
        assertThrows(DomainException.class, () -> TestCase.create(
                PROJECT, "危险文件名", TestCaseType.WEB, null, "sh x", "../x.sh", "echo x", true));
    }

    @Test
    @DisplayName("更新脚本后摘要随之变化")
    void updatesScriptHash() {
        TestCase testCase = TestCase.create(PROJECT, "脚本", TestCaseType.WEB,
                null, null, "run.sh", "echo one", true);
        String original = testCase.getScriptHash();

        testCase.update("脚本", TestCaseType.WEB, null, null, "run.sh", "echo two", true);

        assertNotEquals(original, testCase.getScriptHash());
    }
}
