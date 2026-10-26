-- 项目级产品版本与需求池。
CREATE TABLE `product_version` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`  BIGINT       NOT NULL,
    `version_no`  VARCHAR(64)  NOT NULL,
    `name`        VARCHAR(160) NOT NULL,
    `description` TEXT         DEFAULT NULL,
    `status`      VARCHAR(24)  NOT NULL DEFAULT 'PLANNING',
    `created_by`  BIGINT       NOT NULL,
    `created_at`  DATETIME(3)  NOT NULL,
    `updated_at`  DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_version_project_no` (`project_id`, `version_no`),
    KEY `idx_product_version_project_status` (`project_id`, `status`, `id`),
    CONSTRAINT `fk_product_version_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_product_version_created_by`
        FOREIGN KEY (`created_by`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目产品版本规划';

ALTER TABLE `requirement`
    ADD COLUMN `version_id` BIGINT DEFAULT NULL AFTER `initiative_id`,
    ADD COLUMN `pool_status` VARCHAR(24) NOT NULL DEFAULT 'POOL' AFTER `status`,
    ADD COLUMN `source` VARCHAR(16) NOT NULL DEFAULT 'MANUAL' AFTER `pool_status`,
    ADD COLUMN `review_comment` TEXT DEFAULT NULL AFTER `source`,
    ADD COLUMN `reviewed_by` BIGINT DEFAULT NULL AFTER `review_comment`,
    ADD COLUMN `reviewed_at` DATETIME(3) DEFAULT NULL AFTER `reviewed_by`,
    ADD KEY `idx_requirement_version_pool` (`version_id`, `pool_status`, `id`),
    ADD CONSTRAINT `fk_requirement_product_version`
        FOREIGN KEY (`version_id`) REFERENCES `product_version` (`id`) ON DELETE SET NULL,
    ADD CONSTRAINT `fk_requirement_reviewed_by`
        FOREIGN KEY (`reviewed_by`) REFERENCES `app_user` (`id`);

-- 已进入研发交付的历史需求视为已通过需求评审。
UPDATE `requirement` r
JOIN (
    SELECT DISTINCT `requirement_id`
    FROM `requirement_delivery`
) d ON d.`requirement_id` = r.`id`
SET r.`pool_status` = 'FORMAL';
