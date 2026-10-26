-- 平台发起 Git 提交的独立审计记录。
-- 失败尝试也保留；同一审阅快照最多一条成功记录。
CREATE TABLE `git_commit_record` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`            BIGINT       NOT NULL,
    `task_id`               BIGINT       NOT NULL,
    `task_run_id`           BIGINT       NOT NULL,
    `review_snapshot_id`    BIGINT       NOT NULL,
    `review_decision_id`    BIGINT       NOT NULL,
    `operator_id`           BIGINT       NOT NULL,
    `project_name`          VARCHAR(128) DEFAULT NULL COMMENT '提交时项目名称快照',
    `task_title`            VARCHAR(256) DEFAULT NULL COMMENT '提交时任务标题快照',
    `repository_path`       VARCHAR(2048) NOT NULL COMMENT '提交时仓库绝对路径快照',
    `ticket`                VARCHAR(6)   DEFAULT NULL COMMENT '6 位 Bug 编号；历史回填允许为空',
    `commit_message`        TEXT         NOT NULL,
    `branch_name`           VARCHAR(128) NOT NULL,
    `base_ref`              VARCHAR(128) DEFAULT NULL,
    `commit_sha`            VARCHAR(64)  DEFAULT NULL,
    `file_paths_json`       JSON         NOT NULL,
    `status`                VARCHAR(16)  NOT NULL COMMENT 'RUNNING/SUCCEEDED/FAILED',
    `failure_reason`        VARCHAR(1024) DEFAULT NULL,
    `output_summary`        TEXT         DEFAULT NULL,
    `started_at`            DATETIME(3)  NOT NULL,
    `finished_at`           DATETIME(3)  DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL,
    `updated_at`            DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_git_commit_snapshot_sha` (`review_snapshot_id`, `commit_sha`),
    KEY `idx_git_commit_project_time` (`project_id`, `started_at`, `id`),
    KEY `idx_git_commit_task_time` (`task_id`, `started_at`, `id`),
    KEY `idx_git_commit_sha` (`commit_sha`),
    KEY `idx_git_commit_operator` (`operator_id`, `started_at`),
    CONSTRAINT `fk_git_commit_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_git_commit_task`
        FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_git_commit_task_run`
        FOREIGN KEY (`task_run_id`) REFERENCES `task_run` (`id`),
    CONSTRAINT `fk_git_commit_snapshot`
        FOREIGN KEY (`review_snapshot_id`) REFERENCES `review_snapshot` (`id`),
    CONSTRAINT `fk_git_commit_decision`
        FOREIGN KEY (`review_decision_id`) REFERENCES `review_decision` (`id`),
    CONSTRAINT `fk_git_commit_operator`
        FOREIGN KEY (`operator_id`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '平台发起的 Git 提交尝试与审计记录';

-- 回填现有成功提交。历史上没有独立 Bug 编号字段，ticket 保留为空。
INSERT INTO `git_commit_record` (
    project_id, task_id, task_run_id, review_snapshot_id, review_decision_id,
    operator_id, project_name, task_title, repository_path, ticket,
    commit_message, branch_name, base_ref, commit_sha, file_paths_json,
    status, failure_reason, output_summary, started_at, finished_at,
    created_at, updated_at
)
SELECT
    rs.project_id,
    rs.task_id,
    rs.task_run_id,
    rs.id,
    rd.id,
    rs.committed_by,
    p.name,
    t.title,
    p.path,
    NULL,
    rs.commit_message,
    COALESCE(tr.branch_name, 'HEAD'),
    rs.base_ref,
    rs.commit_sha,
    COALESCE(rs.files_json, JSON_ARRAY()),
    'SUCCEEDED',
    NULL,
    NULL,
    COALESCE(rs.committed_at, rs.updated_at),
    COALESCE(rs.committed_at, rs.updated_at),
    COALESCE(rs.committed_at, rs.updated_at),
    COALESCE(rs.committed_at, rs.updated_at)
FROM review_snapshot rs
JOIN project p ON p.id = rs.project_id
JOIN task t ON t.id = rs.task_id
JOIN task_run tr ON tr.id = rs.task_run_id
JOIN review_decision rd ON rd.id = (
    SELECT MAX(rd2.id)
    FROM review_decision rd2
    WHERE rd2.snapshot_id = rs.id
      AND rd2.decision_type = 'APPROVED'
)
WHERE rs.status = 'COMMITTED'
  AND rs.commit_sha IS NOT NULL
  AND rs.committed_by IS NOT NULL
  AND rs.commit_message IS NOT NULL;
