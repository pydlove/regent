-- 需求记录创建人；历史数据按项目负责人回填。
ALTER TABLE `requirement`
    ADD COLUMN `created_by` BIGINT DEFAULT NULL AFTER `project_id`;

UPDATE `requirement` r
JOIN `project` p ON p.`id` = r.`project_id`
SET r.`created_by` = p.`owner_id`
WHERE r.`created_by` IS NULL
  AND p.`owner_id` IS NOT NULL;

UPDATE `requirement`
SET `created_by` = (SELECT MIN(`id`) FROM `app_user`)
WHERE `created_by` IS NULL;

ALTER TABLE `requirement`
    MODIFY COLUMN `created_by` BIGINT NOT NULL,
    ADD KEY `idx_requirement_created_by` (`created_by`),
    ADD CONSTRAINT `fk_requirement_created_by`
        FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`);
