package com.pydlove.regent.gitcommit.application.query;

import com.pydlove.regent.shared.domain.EntityNotFoundException;

import java.util.List;

/** Git 提交记录查询。账户隔离由查询仓储和任务归属校验共同保证。 */
public class GitCommitQueryService {

    private final GitCommitQueryRepository repository;

    public GitCommitQueryService(GitCommitQueryRepository repository) {
        this.repository = repository;
    }

    public List<GitCommitSummaryView> findByTask(long taskId, long ownerId) {
        return repository.findByTask(taskId, ownerId);
    }

    public GitCommitPageView page(long ownerId, GitCommitFilter filter) {
        return repository.findPage(ownerId, filter);
    }

    public GitCommitDetailView detail(long id, long ownerId) {
        return repository.findDetail(id, ownerId)
                .orElseThrow(() -> new EntityNotFoundException("Git 提交记录不存在：" + id));
    }
}
