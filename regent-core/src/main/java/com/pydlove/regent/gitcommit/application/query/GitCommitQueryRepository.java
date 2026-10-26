package com.pydlove.regent.gitcommit.application.query;

import java.util.List;
import java.util.Optional;

/** Git 提交只读查询端口。 */
public interface GitCommitQueryRepository {

    List<GitCommitSummaryView> findByTask(long taskId, long ownerId);

    GitCommitPageView findPage(long ownerId, GitCommitFilter filter);

    Optional<GitCommitDetailView> findDetail(long id, long ownerId);
}
