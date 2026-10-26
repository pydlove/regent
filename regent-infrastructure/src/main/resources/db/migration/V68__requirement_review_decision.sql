-- 需求评审改为「提交材料 -> 负责人审核」两阶段。
-- 历史记录均为已通过的一次性登记，按已通过回填。
ALTER TABLE `requirement_review_record`
    ADD COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'APPROVED' AFTER `review_date`,
    ADD COLUMN `review_comment` TEXT DEFAULT NULL AFTER `conclusion`,
    ADD COLUMN `decided_by` BIGINT DEFAULT NULL AFTER `reviewed_by`,
    ADD COLUMN `decided_at` DATETIME(3) DEFAULT NULL AFTER `decided_by`,
    ADD KEY `idx_requirement_review_record_version_status`
        (`version_id`, `status`, `review_no`),
    ADD CONSTRAINT `fk_requirement_review_record_decided_by`
        FOREIGN KEY (`decided_by`) REFERENCES `app_user` (`id`);

UPDATE `requirement_review_record`
SET `decided_by` = `reviewed_by`,
    `decided_at` = `updated_at`
WHERE `decided_by` IS NULL;
