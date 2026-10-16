package com.pydlove.regent.governance.application.command;

import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskStatus;
import com.pydlove.regent.execution.domain.repository.TaskRepository;
import com.pydlove.regent.governance.domain.model.ApprovalToken;
import com.pydlove.regent.governance.domain.model.Decision;
import com.pydlove.regent.governance.domain.model.DecisionId;
import com.pydlove.regent.governance.domain.repository.ApprovalTokenRepository;
import com.pydlove.regent.governance.domain.repository.DecisionRepository;
import com.pydlove.regent.shared.domain.DomainException;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 人工审批用例 —— 批准与驳回。
 *
 * <p>这是全项目唯一需要同时改两个聚合的地方，所以它是「跨聚合由应用服务显式编排」这条纪律的
 * 样板：决策单只负责记下「谁在什么时候拍了什么板」，任务只负责按状态机迁到 RUNNING 或 CANCELLED，
 * 两边都不知道对方存在，是这里把它们按顺序接起来。
 *
 * <h2>为什么先做守卫再落库</h2>
 * 两次 {@code save} 之间进程挂掉，会留下「决策已批准、任务还阻塞着」的记录，而这类不一致
 * 没有自动修复路径。所以顺序是：先把两个聚合要做的动作**都在内存里跑一遍**（守卫不通过会直接
 * 抛异常，此时一行都没写），确认两边都合法之后再落库；外面再套一层事务端口，
 * 把「要么全成、要么全不成」交给基础设施。
 *
 * <h2>为什么批准要求任务确实阻塞在这条决策上</h2>
 * {@code task.resume()} 会把 {@code blocking_decision_id} 清空。若任务当前阻塞的是<b>另一条</b>
 * 决策，放行这一条等于顺手解除了另一条待批的阻塞 —— 那条决策还在等人拍板，任务却已经跑起来了。
 * 这种错误一旦发生，事后从数据上完全看不出来。
 */
public class DecisionApprovalService {

    private final DecisionRepository decisionRepository;
    private final ApprovalTokenRepository approvalTokenRepository;
    private final TaskRepository taskRepository;
    private final TransactionRunner transactionRunner;

    public DecisionApprovalService(DecisionRepository decisionRepository,
                                   ApprovalTokenRepository approvalTokenRepository,
                                   TaskRepository taskRepository,
                                   TransactionRunner transactionRunner) {
        this.decisionRepository = decisionRepository;
        this.approvalTokenRepository = approvalTokenRepository;
        this.taskRepository = taskRepository;
        this.transactionRunner = transactionRunner;
    }

    /** 批准：决策落 APPROVED，任务从断点续跑。 */
    public Decision approve(long decisionId, ReviewCommand command) {
        return transactionRunner.inTransaction(() -> review(decisionId, command, true));
    }

    /** 驳回：决策落 REJECTED，阻塞中的任务终止。 */
    public Decision reject(long decisionId, ReviewCommand command) {
        return transactionRunner.inTransaction(() -> review(decisionId, command, false));
    }

    private Decision review(long decisionId, ReviewCommand command, boolean approve) {
        DecisionId id = DecisionId.of(decisionId);
        Decision decision = decisionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("决策不存在：" + decisionId));
        Task task = taskRepository.findById(decision.getTaskId())
                .orElseThrow(() -> new EntityNotFoundException("决策所属任务不存在：" + decision.getTaskId()));

        // 令牌先核销资格、后核销：核销动作要等决策真的落库了再做，否则校验通过但落库失败，
        // 令牌就白白作废了 —— 人从邮件点进来只看到一句「令牌已失效」，再点一次还是失效。
        ApprovalToken token = verifyToken(decision.getId(), command);

        TaskStatus before = task.getStatus();
        if (approve) {
            // 两个聚合的守卫都在任何写操作之前跑完：这里的顺序意味着「重复点批准」会报
            // 「任务不在阻塞中」而不是「决策已决议」—— 前者是后者的后果，说的也是同一件事，
            // 而这样排序换来的是「一个被拒绝的请求不会在内存里留下任何已迁移的对象」。
            requireBlockedByThisDecision(task, decision.getId());
            decision.approve(command.choice(), command.note());
            task.resume();
        } else {
            decision.reject(command.choice(), command.note());
            // 任务已经不在这条决策上（比如被人工直接终止了）时只留决策记录：硬要给一个终态任务
            // 判终止只会抛异常，把一次正常的人工否决变成 500，而决策单会永远停在待批。
            if (task.getStatus() == TaskStatus.BLOCKED) {
                task.cancel();
            }
        }

        // 状态没动就不写任务：空 UPDATE 会把 updated_at 刷新一遍，而那一列的含义是
        // 「最后一次状态变化的时刻」—— 无谓地写它，等于把这条时间线的含义稀释掉。
        if (task.getStatus() != before) {
            taskRepository.save(task);
        }
        decisionRepository.save(decision);
        if (token != null) {
            approvalTokenRepository.consume(token.id(), command.clientIp(), now());
        }
        return decision;
    }

    private ApprovalToken verifyToken(DecisionId decisionId, ReviewCommand command) {
        String rawToken = command.token();
        if (rawToken == null || rawToken.isBlank()) {
            // 控制台路径（本机/内网直连、尚无登录体系）不带令牌。这条口子写在注释里而不是
            // 藏在代码里：哪天平台上了公网，这里必须改成「一律要求令牌」。
            return null;
        }
        ApprovalToken token = approvalTokenRepository.findByHash(ApprovalToken.hashOf(rawToken))
                .orElseThrow(() -> new DomainException("审批令牌无效"));
        if (!token.decisionId().equals(decisionId)) {
            throw new DomainException("审批令牌与这条决策不匹配");
        }
        if (!token.isUsable(now())) {
            throw new DomainException("审批令牌已使用或已过期，请重新发起审批");
        }
        return token;
    }

    private void requireBlockedByThisDecision(Task task, DecisionId decisionId) {
        if (task.getStatus() != TaskStatus.BLOCKED) {
            throw new DomainException("任务 " + task.getId() + " 不在阻塞中，无法按这条决策续跑，当前状态："
                    + task.getStatus());
        }
        if (!task.getBlockingDecisionId().equals(Optional.of(decisionId))) {
            throw new DomainException("任务 " + task.getId() + " 当前阻塞在另一条决策上："
                    + task.getBlockingDecisionId().map(DecisionId::toString).orElse("（未知）"));
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
