package com.pydlove.regent.review.application.command;

import com.pydlove.regent.execution.application.command.CreateTaskContinuationCommand;
import com.pydlove.regent.execution.application.command.TaskCommandService;
import com.pydlove.regent.execution.domain.model.Task;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.review.domain.model.ReviewChangeUnitId;
import com.pydlove.regent.review.domain.model.ReviewDecision;
import com.pydlove.regent.review.domain.model.ReviewDecisionType;
import com.pydlove.regent.review.domain.model.ReviewFindingId;
import com.pydlove.regent.review.domain.model.ReviewFindingStatus;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewOperatorId;
import com.pydlove.regent.review.domain.model.ReviewUnitDecisionStatus;
import com.pydlove.regent.review.domain.repository.ReviewDecisionRepository;
import com.pydlove.regent.review.domain.repository.ReviewSnapshotRepository;
import com.pydlove.regent.shared.domain.EntityNotFoundException;
import com.pydlove.regent.shared.domain.TransactionRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 人工查看、发现状态与验收结论的唯一写入口。 */
public class ReviewDecisionService {

    private final ReviewSnapshotRepository snapshotRepository;
    private final ReviewDecisionRepository decisionRepository;
    private final TaskCommandService taskCommandService;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public ReviewDecisionService(ReviewSnapshotRepository snapshotRepository,
                                 ReviewDecisionRepository decisionRepository,
                                 TaskCommandService taskCommandService,
                                 TransactionRunner transactionRunner) {
        this(snapshotRepository, decisionRepository, taskCommandService,
                transactionRunner, Clock.systemUTC());
    }

    ReviewDecisionService(ReviewSnapshotRepository snapshotRepository,
                          ReviewDecisionRepository decisionRepository,
                          TaskCommandService taskCommandService,
                          TransactionRunner transactionRunner,
                          Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.decisionRepository = decisionRepository;
        this.taskCommandService = taskCommandService;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public ReviewSnapshot markViewed(ReviewTaskId taskId, ReviewChangeUnitId unitId) {
        return transactionRunner.inTransaction(() -> {
            ReviewSnapshot snapshot = require(taskId);
            if (snapshot.markViewed(unitId, now())) {
                snapshotRepository.save(snapshot);
            }
            return snapshot;
        });
    }

    public ReviewSnapshot markFinding(ReviewTaskId taskId,
                                      ReviewFindingId findingId,
                                      ReviewFindingStatus status) {
        return transactionRunner.inTransaction(() -> {
            ReviewSnapshot snapshot = require(taskId);
            snapshot.markFinding(findingId, status, now());
            snapshotRepository.save(snapshot);
            return snapshot;
        });
    }

    public ReviewSnapshot decideUnit(ReviewTaskId taskId,
                                     ReviewChangeUnitId unitId,
                                     ReviewUnitDecisionStatus decision,
                                     ReviewOperatorId operatorId,
                                     String comment) {
        return transactionRunner.inTransaction(() -> {
            ReviewSnapshot snapshot = require(taskId);
            snapshot.decideUnit(unitId, decision, operatorId, comment, now());
            snapshotRepository.save(snapshot);
            return snapshot;
        });
    }

    public ReviewDecisionResult decide(ReviewDecisionCommand command) {
        return transactionRunner.inTransaction(() -> {
            ReviewSnapshot snapshot = require(command.taskId());
            Instant now = now();
            switch (command.decision()) {
                case APPROVED -> snapshot.approve(now);
                case CHANGES_REQUESTED -> snapshot.requestChanges(now);
                case REJECTED -> snapshot.reject(now);
            }
            snapshotRepository.save(snapshot);

            ReviewDecision decision = ReviewDecision.create(
                    snapshot.getId(), command.decision(), command.operatorId(),
                    command.comment(), now);
            decisionRepository.save(decision);

            Long continuationTaskId = null;
            if (command.decision() == ReviewDecisionType.CHANGES_REQUESTED) {
                Task continuation = taskCommandService.createContinuation(
                        new CreateTaskContinuationCommand(
                                TaskId.of(command.taskId().value()),
                                revisionPrompt(command.comment(), snapshot),
                                0));
                continuationTaskId = continuation.getId().value();
            }
            return new ReviewDecisionResult(snapshot, decision, continuationTaskId);
        });
    }

    private ReviewSnapshot require(ReviewTaskId taskId) {
        return snapshotRepository.findCurrentByTask(taskId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "任务还没有审阅快照：" + taskId.value()));
    }

    private static String revisionPrompt(String comment, ReviewSnapshot snapshot) {
        String unitComments = snapshot.getChangeUnits().stream()
                .filter(unit -> unit.getDecisionStatus()
                        == ReviewUnitDecisionStatus.CHANGES_REQUESTED)
                .map(unit -> "- " + unit.getTitle() + "：" + unit.getDecisionComment())
                .collect(java.util.stream.Collectors.joining("\n"));
        String detail = unitComments.isBlank()
                ? comment
                : comment + "\n\n逐项修改要求：\n" + unitComments;
        return "审阅要求修改：\n\n" + detail
                + "\n\n请沿用原任务上下文，只修正上述问题；完成后重新生成审阅快照。";
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
