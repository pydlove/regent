package com.pydlove.regent.review.interfaces.rest;

import com.pydlove.regent.execution.application.query.TaskQueryService;
import com.pydlove.regent.execution.application.query.TaskRunQueryService;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import com.pydlove.regent.review.application.command.ReviewDecisionCommand;
import com.pydlove.regent.review.application.command.ReviewDecisionResult;
import com.pydlove.regent.review.application.command.ReviewDecisionService;
import com.pydlove.regent.review.application.command.ReviewGitCommitCommand;
import com.pydlove.regent.review.application.command.ReviewGitCommitService;
import com.pydlove.regent.review.application.command.ReviewSnapshotService;
import com.pydlove.regent.review.application.query.ReviewQueryService;
import com.pydlove.regent.review.domain.model.ReviewChangeUnitId;
import com.pydlove.regent.review.domain.model.ReviewDecisionType;
import com.pydlove.regent.review.domain.model.ReviewFindingId;
import com.pydlove.regent.review.domain.model.ReviewFindingStatus;
import com.pydlove.regent.review.domain.model.ReviewOperatorId;
import com.pydlove.regent.review.domain.model.ReviewSnapshot;
import com.pydlove.regent.review.domain.model.ReviewTaskId;
import com.pydlove.regent.review.domain.model.ReviewTaskRunId;
import com.pydlove.regent.review.domain.model.ReviewUnitDecisionStatus;
import com.pydlove.regent.review.interfaces.dto.ReviewDecisionActionResponse;
import com.pydlove.regent.review.interfaces.dto.ReviewDecisionRequest;
import com.pydlove.regent.review.interfaces.dto.ReviewDiffResponse;
import com.pydlove.regent.review.interfaces.dto.ReviewFindingStatusRequest;
import com.pydlove.regent.review.interfaces.dto.ReviewGitCommitRequest;
import com.pydlove.regent.review.interfaces.dto.ReviewGitCommitResponse;
import com.pydlove.regent.review.interfaces.dto.ReviewSnapshotResponse;
import com.pydlove.regent.review.interfaces.dto.TaskReviewResponse;
import com.pydlove.regent.review.interfaces.dto.ReviewUnitDecisionRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 任务详情内的审阅接口，不新增独立审阅入口。 */
@RestController
@RequestMapping("/api/tasks/{taskId}/review")
public class ReviewController {

    private final TaskQueryService taskQueryService;
    private final TaskRunQueryService taskRunQueryService;
    private final ReviewSnapshotService snapshotService;
    private final ReviewDecisionService decisionService;
    private final ReviewGitCommitService gitCommitService;
    private final ReviewQueryService queryService;

    public ReviewController(TaskQueryService taskQueryService,
                            TaskRunQueryService taskRunQueryService,
                            ReviewSnapshotService snapshotService,
                            ReviewDecisionService decisionService,
                            ReviewGitCommitService gitCommitService,
                            ReviewQueryService queryService) {
        this.taskQueryService = taskQueryService;
        this.taskRunQueryService = taskRunQueryService;
        this.snapshotService = snapshotService;
        this.decisionService = decisionService;
        this.gitCommitService = gitCommitService;
        this.queryService = queryService;
    }

    @GetMapping
    public TaskReviewResponse get(@PathVariable long taskId, HttpServletRequest request) {
        ReviewTaskId id = requireTask(taskId, request);
        ReviewSnapshot snapshot = snapshotService.current(id).orElse(null);
        List<com.pydlove.regent.review.domain.model.ReviewDecision> decisions = snapshot == null
                ? List.of()
                : queryService.decisions(snapshot.getId());
        return TaskReviewResponse.of(snapshot, decisions, snapshotService.progress(id));
    }

    @PostMapping("/generate")
    public TaskReviewResponse generate(@PathVariable long taskId, HttpServletRequest request) {
        ReviewTaskId id = requireTask(taskId, request);
        ReviewTaskRunId runId = latestSuccessRunId(taskId, request);
        snapshotService.requestGeneration(id, runId);
        return reviewResponse(id);
    }

    @PostMapping("/regenerate")
    public TaskReviewResponse regenerate(@PathVariable long taskId, HttpServletRequest request) {
        ReviewTaskId id = requireTask(taskId, request);
        ReviewTaskRunId runId = latestSuccessRunId(taskId, request);
        snapshotService.requestRegeneration(id, runId);
        return reviewResponse(id);
    }

    private TaskReviewResponse reviewResponse(ReviewTaskId id) {
        ReviewSnapshot snapshot = snapshotService.current(id).orElse(null);
        List<com.pydlove.regent.review.domain.model.ReviewDecision> decisions = snapshot == null
                ? List.of()
                : queryService.decisions(snapshot.getId());
        return TaskReviewResponse.of(snapshot, decisions, snapshotService.progress(id));
    }

    @PostMapping("/units/{unitId}/viewed")
    public ReviewSnapshotResponse markViewed(@PathVariable long taskId,
                                             @PathVariable long unitId,
                                             HttpServletRequest request) {
        ReviewTaskId id = requireTask(taskId, request);
        return ReviewSnapshotResponse.from(
                decisionService.markViewed(id, ReviewChangeUnitId.of(unitId)));
    }

    @PostMapping("/units/{unitId}/decision")
    public ReviewSnapshotResponse decideUnit(@PathVariable long taskId,
                                             @PathVariable long unitId,
                                             @RequestBody ReviewUnitDecisionRequest body,
                                             HttpServletRequest request) {
        ReviewTaskId id = requireTask(taskId, request);
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return ReviewSnapshotResponse.from(decisionService.decideUnit(
                id,
                ReviewChangeUnitId.of(unitId),
                ReviewUnitDecisionStatus.valueOf(body.decision().strip().toUpperCase()),
                ReviewOperatorId.of(user.id()),
                body.comment()));
    }

    @PatchMapping("/findings/{findingId}")
    public ReviewSnapshotResponse markFinding(@PathVariable long taskId,
                                              @PathVariable long findingId,
                                              @RequestBody ReviewFindingStatusRequest body,
                                              HttpServletRequest request) {
        ReviewTaskId id = requireTask(taskId, request);
        return ReviewSnapshotResponse.from(decisionService.markFinding(
                id,
                ReviewFindingId.of(findingId),
                ReviewFindingStatus.valueOf(body.status().strip().toUpperCase())));
    }

    @PostMapping("/decisions")
    public ReviewDecisionActionResponse decide(@PathVariable long taskId,
                                               @RequestBody ReviewDecisionRequest body,
                                               HttpServletRequest request) {
        ReviewTaskId id = requireTask(taskId, request);
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        ReviewDecisionResult result = decisionService.decide(new ReviewDecisionCommand(
                id,
                ReviewDecisionType.valueOf(body.decision().strip().toUpperCase()),
                ReviewOperatorId.of(user.id()),
                body.comment()));
        return ReviewDecisionActionResponse.from(result);
    }

    @GetMapping("/diff")
    public ReviewDiffResponse diff(@PathVariable long taskId, HttpServletRequest request) {
        ReviewTaskId id = requireTask(taskId, request);
        ReviewSnapshot snapshot = queryService.requireCurrent(id);
        return new ReviewDiffResponse(snapshot.getDiffContent(), snapshot.isDiffTruncated());
    }

    @GetMapping("/git-commit")
    public ReviewGitCommitResponse gitCommitPreview(@PathVariable long taskId,
                                                    HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        ReviewTaskId id = requireTask(taskId, request);
        return ReviewGitCommitResponse.from(
                gitCommitService.preview(id, UserId.of(user.id())));
    }

    @PostMapping("/git-commit")
    public ReviewGitCommitResponse gitCommit(@PathVariable long taskId,
                                             @RequestBody ReviewGitCommitRequest body,
                                             HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        ReviewTaskId id = requireTask(taskId, request);
        return ReviewGitCommitResponse.from(gitCommitService.commit(
                new ReviewGitCommitCommand(
                        id,
                        ReviewOperatorId.of(user.id()),
                        body.message())));
    }

    private ReviewTaskId requireTask(long taskId, HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        taskQueryService.get(TaskId.of(taskId), UserId.of(user.id()));
        return ReviewTaskId.of(taskId);
    }

    private ReviewTaskRunId latestSuccessRunId(long taskId, HttpServletRequest request) {
        return ReviewTaskRunId.of(taskRunQueryService.latestSuccess(TaskId.of(taskId)).getId().value());
    }
}
