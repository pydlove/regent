package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 派发前不再预建分支，是否隔离由执行后的 AI 分支意图决定。 */
class BranchProvisioningServiceTest {

    private final ExecutionFakes.Branches branches = new ExecutionFakes.Branches();
    private final ProjectProfile project = ExecutionFakes.Projects.profile(1L, DecisionLevel.L3, true);
    private final BranchProvisioningService service = new BranchProvisioningService(branches);

    @Test
    @DisplayName("默认直接在当前分支执行，不创建分支也不生成审批")
    void 默认使用当前分支() {
        Task task = task();

        BranchPreparation preparation = service.prepare(task, project);

        assertTrue(preparation.ready());
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertEquals("main", task.getBranchName().orElseThrow());
        assertTrue(branches.created.isEmpty());
    }

    @Test
    @DisplayName("非 Git 目录或多仓库父目录没有分支名，仍然可以派发")
    void 无分支目录也能派发() {
        branches.current = null;
        Task task = task();

        BranchPreparation preparation = service.prepare(task, project);

        assertTrue(preparation.ready());
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertTrue(task.getBranchName().isEmpty());
        assertNull(preparation.branchName());
    }

    private static Task task() {
        Task task = Task.manual(ProjectId.of(1L), "实现页面", "实现一个页面", 0);
        task.assignIdentity(TaskId.of(7L));
        return task;
    }
}
