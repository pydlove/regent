-- 立项增强字段：干系人、目标版本和主要风险。
ALTER TABLE `initiative`
    ADD COLUMN `stakeholders` TEXT DEFAULT NULL AFTER `success_metrics`,
    ADD COLUMN `target_version` VARCHAR(64) DEFAULT NULL AFTER `stakeholders`,
    ADD COLUMN `risk_text` TEXT DEFAULT NULL AFTER `target_version`;

-- 需求归属于立项。历史需求从已有交付单回填，未进入交付的需求保持 NULL。
ALTER TABLE `requirement`
    ADD COLUMN `initiative_id` BIGINT DEFAULT NULL AFTER `project_id`,
    ADD KEY `idx_requirement_initiative` (`initiative_id`, `deleted`),
    ADD CONSTRAINT `fk_requirement_initiative`
        FOREIGN KEY (`initiative_id`) REFERENCES `initiative` (`id`) ON DELETE SET NULL;

UPDATE `requirement` r
JOIN (
    SELECT `requirement_id`, MIN(`initiative_id`) AS `initiative_id`
    FROM `requirement_delivery`
    GROUP BY `requirement_id`
) rd ON rd.`requirement_id` = r.`id`
SET r.`initiative_id` = rd.`initiative_id`
WHERE r.`initiative_id` IS NULL;
