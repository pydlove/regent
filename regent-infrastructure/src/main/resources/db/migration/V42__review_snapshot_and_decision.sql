-- =====================================================================================
-- V42：AI 变更审阅与验收门禁
--
-- 1) task_run 固定执行起点，审阅快照才能从“这次任务开始时”计算 diff；
-- 2) review_snapshot 保存不可变的代码事实与独立审阅状态；
-- 3) change_unit / finding / evidence 属于快照聚合，随快照读取与保存；
-- 4) review_decision 只追加，作为人工验收与部署审计依据；
-- 5) deployment_run 关联任务、快照与人工结论，正式部署可追溯。
-- =====================================================================================

ALTER TABLE `task_run`
    ADD COLUMN `base_ref` VARCHAR(128) DEFAULT NULL
        COMMENT '本次执行开始时的 Git HEAD；非 Git 项目为空' AFTER `attempt_no`;

CREATE TABLE `review_snapshot` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`            BIGINT       NOT NULL,
    `task_run_id`        BIGINT       NOT NULL,
    `project_id`         BIGINT       NOT NULL,
    `base_ref`           VARCHAR(128) DEFAULT NULL,
    `head_ref`           VARCHAR(128) DEFAULT NULL,
    `git_repository`     TINYINT(1)   NOT NULL DEFAULT 1,
    `diff_hash`          CHAR(64)     NOT NULL COMMENT '完整 diff 与文件元信息的稳定 SHA-256',
    `file_count`         INT          NOT NULL DEFAULT 0,
    `addition_count`     INT          NOT NULL DEFAULT 0,
    `deletion_count`     INT          NOT NULL DEFAULT 0,
    `status`             VARCHAR(24)  NOT NULL COMMENT 'WAITING_REVIEW/IN_REVIEW/CHANGES_REQUESTED/APPROVED/REJECTED/EXPIRED',
    `diff_content`       LONGTEXT     DEFAULT NULL COMMENT 'unified diff 或非 Git 文件清单；超限时截断',
    `diff_truncated`     TINYINT(1)   NOT NULL DEFAULT 0,
    `analysis_status`    VARCHAR(16)  NOT NULL COMMENT 'PENDING/SUCCEEDED/FAILED/SKIPPED',
    `analysis_error`     VARCHAR(1024) DEFAULT NULL,
    `reviewed_at`        DATETIME(3)  DEFAULT NULL,
    `created_at`         DATETIME(3)  NOT NULL,
    `updated_at`         DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_review_snapshot_run` (`task_run_id`),
    KEY `idx_review_snapshot_task` (`task_id`, `id`),
    KEY `idx_review_snapshot_project_status` (`project_id`, `status`),
    CONSTRAINT `fk_review_snapshot_task`
        FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_review_snapshot_run`
        FOREIGN KEY (`task_run_id`) REFERENCES `task_run` (`id`),
    CONSTRAINT `fk_review_snapshot_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '一次任务尝试的审阅快照';

CREATE TABLE `review_change_unit` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `snapshot_id`      BIGINT       NOT NULL,
    `sequence_no`      INT          NOT NULL,
    `kind`             VARCHAR(24)  NOT NULL COMMENT 'BUSINESS/GENERATED/LOCKFILE/FORMATTING/BINARY/UNKNOWN',
    `risk_level`       VARCHAR(16)  NOT NULL COMMENT 'LOW/MEDIUM/HIGH',
    `title`            VARCHAR(256) NOT NULL,
    `reason`           TEXT         NOT NULL,
    `module_name`      VARCHAR(128) DEFAULT NULL,
    `file_paths_json`  JSON         NOT NULL,
    `key_hunks_json`   JSON         DEFAULT NULL,
    `requires_view`    TINYINT(1)   NOT NULL DEFAULT 0,
    `viewed_at`        DATETIME(3)  DEFAULT NULL,
    `created_at`       DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_review_unit_sequence` (`snapshot_id`, `sequence_no`),
    CONSTRAINT `fk_review_unit_snapshot`
        FOREIGN KEY (`snapshot_id`) REFERENCES `review_snapshot` (`id`)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '按业务意图组织的审阅单元';

CREATE TABLE `review_evidence` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `snapshot_id`      BIGINT       NOT NULL,
    `change_unit_id`   BIGINT       DEFAULT NULL,
    `evidence_type`    VARCHAR(32)  NOT NULL,
    `status`           VARCHAR(24)  NOT NULL COMMENT 'PASSED/FAILED/NOT_EXECUTED/UNAVAILABLE',
    `title`            VARCHAR(256) NOT NULL,
    `command_text`     VARCHAR(2048) DEFAULT NULL,
    `summary`          TEXT         DEFAULT NULL,
    `source_type`      VARCHAR(32)  NOT NULL COMMENT 'TASK_RUN/TEST_CASE_RUN/EVENT/MANUAL/...',
    `source_id`        BIGINT       DEFAULT NULL,
    `occurred_at`      DATETIME(3)  NOT NULL,
    `created_at`       DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_review_evidence_snapshot` (`snapshot_id`, `id`),
    KEY `idx_review_evidence_unit` (`change_unit_id`),
    CONSTRAINT `fk_review_evidence_snapshot`
        FOREIGN KEY (`snapshot_id`) REFERENCES `review_snapshot` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_review_evidence_unit`
        FOREIGN KEY (`change_unit_id`) REFERENCES `review_change_unit` (`id`)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '审阅验证证据';

CREATE TABLE `review_finding` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `snapshot_id`      BIGINT       NOT NULL,
    `change_unit_id`   BIGINT       DEFAULT NULL,
    `severity`         VARCHAR(16)  NOT NULL COMMENT 'BLOCKER/CRITICAL/MAJOR/MINOR/INFO',
    `status`           VARCHAR(24)  NOT NULL COMMENT 'PENDING/CONFIRMED/FALSE_POSITIVE/FIXED',
    `title`            VARCHAR(256) NOT NULL,
    `description`      TEXT         NOT NULL,
    `impact`           TEXT         DEFAULT NULL,
    `file_path`        VARCHAR(1024) DEFAULT NULL,
    `line_start`       INT          DEFAULT NULL,
    `line_end`         INT          DEFAULT NULL,
    `suggested_action` TEXT         DEFAULT NULL,
    `evidence_id`      BIGINT       DEFAULT NULL,
    `created_at`       DATETIME(3)  NOT NULL,
    `updated_at`       DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_review_finding_snapshot` (`snapshot_id`, `severity`, `status`),
    KEY `idx_review_finding_unit` (`change_unit_id`),
    KEY `idx_review_finding_evidence` (`evidence_id`),
    CONSTRAINT `fk_review_finding_snapshot`
        FOREIGN KEY (`snapshot_id`) REFERENCES `review_snapshot` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_review_finding_unit`
        FOREIGN KEY (`change_unit_id`) REFERENCES `review_change_unit` (`id`)
        ON DELETE SET NULL,
    CONSTRAINT `fk_review_finding_evidence`
        FOREIGN KEY (`evidence_id`) REFERENCES `review_evidence` (`id`)
        ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'AI 或规则提出的审阅发现';

CREATE TABLE `review_decision` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `snapshot_id`      BIGINT       NOT NULL,
    `decision_type`    VARCHAR(24)  NOT NULL COMMENT 'APPROVED/CHANGES_REQUESTED/REJECTED',
    `operator_id`      BIGINT       NOT NULL,
    `comment_text`     TEXT         DEFAULT NULL,
    `created_at`       DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_review_decision_snapshot` (`snapshot_id`, `id`),
    KEY `idx_review_decision_operator` (`operator_id`, `created_at`),
    CONSTRAINT `fk_review_decision_snapshot`
        FOREIGN KEY (`snapshot_id`) REFERENCES `review_snapshot` (`id`),
    CONSTRAINT `fk_review_decision_operator`
        FOREIGN KEY (`operator_id`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '人工验收结论，只追加不修改';

ALTER TABLE `deployment_run`
    ADD COLUMN `task_id` BIGINT DEFAULT NULL AFTER `project_id`,
    ADD COLUMN `review_snapshot_id` BIGINT DEFAULT NULL AFTER `task_id`,
    ADD COLUMN `review_decision_id` BIGINT DEFAULT NULL AFTER `review_snapshot_id`,
    ADD KEY `idx_deployment_run_task` (`task_id`, `id`),
    ADD KEY `idx_deployment_run_review` (`review_snapshot_id`, `review_decision_id`),
    ADD CONSTRAINT `fk_deployment_run_task`
        FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    ADD CONSTRAINT `fk_deployment_run_review_snapshot`
        FOREIGN KEY (`review_snapshot_id`) REFERENCES `review_snapshot` (`id`),
    ADD CONSTRAINT `fk_deployment_run_review_decision`
        FOREIGN KEY (`review_decision_id`) REFERENCES `review_decision` (`id`);
