package com.pydlove.regent.gitcommit.interfaces.rest;

import com.pydlove.regent.execution.application.query.TaskQueryService;
import com.pydlove.regent.execution.domain.model.TaskId;
import com.pydlove.regent.gitcommit.application.query.GitCommitFilter;
import com.pydlove.regent.gitcommit.application.query.GitCommitQueryService;
import com.pydlove.regent.gitcommit.domain.model.GitCommitStatus;
import com.pydlove.regent.gitcommit.interfaces.dto.GitCommitDetailResponse;
import com.pydlove.regent.gitcommit.interfaces.dto.GitCommitPageResponse;
import com.pydlove.regent.gitcommit.interfaces.dto.GitCommitSummaryResponse;
import com.pydlove.regent.identity.application.query.AuthenticatedUser;
import com.pydlove.regent.identity.domain.model.UserId;
import com.pydlove.regent.identity.interfaces.rest.AuthSessionInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Git 提交历史查询，不提供修改或删除入口。 */
@RestController
@RequestMapping("/api")
public class GitCommitController {

    private final GitCommitQueryService queryService;
    private final TaskQueryService taskQueryService;

    public GitCommitController(GitCommitQueryService queryService,
                               TaskQueryService taskQueryService) {
        this.queryService = queryService;
        this.taskQueryService = taskQueryService;
    }

    @GetMapping("/tasks/{taskId}/git-commits")
    public List<GitCommitSummaryResponse> byTask(@PathVariable long taskId,
                                                HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        taskQueryService.get(TaskId.of(taskId), UserId.of(user.id()));
        return queryService.findByTask(taskId, user.id()).stream()
                .map(GitCommitSummaryResponse::from)
                .toList();
    }

    @GetMapping("/git-commits")
    public GitCommitPageResponse page(@RequestParam(required = false) Long projectId,
                                      @RequestParam(required = false) Long taskId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        GitCommitFilter filter = new GitCommitFilter(
                projectId,
                taskId,
                status == null || status.isBlank()
                        ? null
                        : GitCommitStatus.valueOf(status.strip().toUpperCase()),
                keyword,
                parseInstant(from),
                parseInstant(to),
                page,
                size);
        return GitCommitPageResponse.from(queryService.page(user.id(), filter));
    }

    @GetMapping("/git-commits/{id}")
    public GitCommitDetailResponse detail(@PathVariable long id,
                                          HttpServletRequest request) {
        AuthenticatedUser user = AuthSessionInterceptor.currentUser(request);
        return GitCommitDetailResponse.from(queryService.detail(id, user.id()));
    }

    private static Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value.strip());
    }
}
