-- =====================================================================================
-- V44：审阅通过后的 Git 提交闭环
--
-- 1) 快照保存固定文件清单，提交时只允许暂存这些文件；
-- 2) 快照记录最终提交说明、提交 SHA、提交人与时间，形成可追溯审计事实；
-- 3) 企业可配置提交模板和校验正则，设置由独立单行表保存。
-- =====================================================================================

ALTER TABLE `review_snapshot`
    ADD COLUMN `files_json` JSON DEFAULT NULL AFTER `deletion_count`,
    ADD COLUMN `commit_sha` VARCHAR(64) DEFAULT NULL AFTER `analysis_error`,
    ADD COLUMN `commit_message` TEXT DEFAULT NULL AFTER `commit_sha`,
    ADD COLUMN `committed_by` BIGINT DEFAULT NULL AFTER `commit_message`,
    ADD COLUMN `committed_at` DATETIME(3) DEFAULT NULL AFTER `committed_by`,
    MODIFY COLUMN `status` VARCHAR(24) NOT NULL
        COMMENT 'WAITING_REVIEW/IN_REVIEW/CHANGES_REQUESTED/APPROVED/COMMITTED/REJECTED/EXPIRED',
    ADD KEY `idx_review_snapshot_commit` (`commit_sha`),
    ADD KEY `idx_review_snapshot_committed_by` (`committed_by`, `committed_at`),
    ADD CONSTRAINT `fk_review_snapshot_committed_by`
        FOREIGN KEY (`committed_by`) REFERENCES `app_user` (`id`);

CREATE TABLE `git_commit_setting` (
    `id`                   TINYINT      NOT NULL,
    `enabled`              TINYINT(1)   NOT NULL DEFAULT 1,
    `default_type`         VARCHAR(32)  NOT NULL DEFAULT 'feat',
    `default_scope`        VARCHAR(64)  DEFAULT NULL,
    `subject_template`     VARCHAR(512) NOT NULL,
    `body_template`        TEXT         DEFAULT NULL,
    `validation_pattern`   VARCHAR(1024) DEFAULT NULL,
    `validation_hint`      VARCHAR(512) DEFAULT NULL,
    `updated_at`           DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '企业 Git 提交信息生成模板与校验规范';
