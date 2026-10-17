package com.pydlove.regent.execution.application.command;

import com.pydlove.regent.execution.domain.model.BranchApprovalState;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.governance.application.command.DecisionRaiseService;
import com.pydlove.regent.project.domain.model.ProjectId;
import com.pydlove.regent.project.domain.repository.ProjectProfile;
import com.pydlove.regent.shared.kernel.DecisionLevel;
import com.pydlove.regent.support.ExecutionFakes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分支准备：审批必须发生在 Git 写操作之前。 */
class BranchProvisioningServiceTest {

    private final ExecutionFakes.Branches branches = new ExecutionFakes.Branches();
    private final ExecutionFakes.Decisions decisions = new ExecutionFakes.Decisions();
    private final ExecutionFakes.Rules rules = new ExecutionFakes.Rules();
    private final ProjectProfile project = ExecutionFakes.Projects.profile(1L, DecisionLevel.L3, true);

    private BranchProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new BranchProvisioningService(branches, new DecisionRaiseService(rules, decisions));
    }

    @Test
    @DisplayName("需要新建分支时先挂起，审批前不调用 Git")
    void 新分支先审批() {
        branches.requiresCreation = true;
        Task task = task();

        BranchPreparation preparation = service.prepare(task, project);

        assertFalse(preparation.ready());
        assertEquals(TaskStatus.BLOCKED, task.getStatus());
        assertEquals(BranchApprovalState.PENDING, task.getBranchApprovalState());
        assertTrue(branches.created.isEmpty(), "审批前创建分支就是绕过人工控制");
        assertEquals(1, decisions.saves);
        assertEquals(DecisionLevel.L2, decisions.all().get(0).getEffectiveLevel());
        assertTrue(decisions.all().get(0).isPending(), "项目阈值再高也不能自动批准分支创建");
    }

    @Test
    @DisplayName("已有分支直接复用，不重复审批")
    void 已有分支直接使用() {
        Task task = task();

        BranchPreparation preparation = service.prepare(task, project);

        assertTrue(preparation.ready());
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertEquals("codex/task-7", task.getBranchName().orElseThrow());
        assertEquals(0, decisions.saves);
    }

    @Test
    @DisplayName("人工批准后才创建分支并清掉一次性批准状态")
    void 批准后创建分支() {
        branches.requiresCreation = true;
        Task task = task();
        service.prepare(task, project);
        task.approveBranchCreation();

        BranchPreparation preparation = service.prepare(task, project);

        assertTrue(preparation.ready());
        assertEquals(TaskStatus.RUNNING, task.getStatus());
        assertEquals(BranchApprovalState.NONE, task.getBranchApprovalState());
        assertEquals(1, branches.created.size());
    }

    private static Task task() {
        Task task = Task.manual(ProjectId.of(1L), "实现页面", "实现一个页面", 0);
        task.assignIdentity(TaskId.of(7L));
        return task;
    }
}
