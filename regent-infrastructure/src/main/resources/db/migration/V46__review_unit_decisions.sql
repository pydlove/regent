-- 逐 diff 审阅：每个变更单元给出关键结论、需人工决策的问题和独立结论。
ALTER TABLE `review_change_unit`
    ADD COLUMN `decision_question` TEXT DEFAULT NULL AFTER `reason`,
    ADD COLUMN `decision_status` VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER `viewed_at`,
    ADD COLUMN `decision_comment` TEXT DEFAULT NULL AFTER `decision_status`,
    ADD COLUMN `decided_by` BIGINT DEFAULT NULL AFTER `decision_comment`,
    ADD COLUMN `decided_at` DATETIME(3) DEFAULT NULL AFTER `decided_by`,
    ADD KEY `idx_review_unit_decision` (`snapshot_id`, `decision_status`),
    ADD KEY `idx_review_unit_decided_by` (`decided_by`, `decided_at`),
    ADD CONSTRAINT `fk_review_unit_decided_by`
        FOREIGN KEY (`decided_by`) REFERENCES `app_user` (`id`);
