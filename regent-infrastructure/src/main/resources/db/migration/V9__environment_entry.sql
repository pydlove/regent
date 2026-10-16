-- 环境信息。project_id 为空表示全局；项目级同键值在执行时覆盖全局值。
-- owner_id 是由 project_id 派生的唯一键组成部分：全局统一为 0，项目为项目 ID，
-- 从而让 MySQL 对 NULL 不做唯一约束的默认行为不会放过重复全局键。
CREATE TABLE `environment_entry` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`  BIGINT       DEFAULT NULL COMMENT 'NULL 表示全局，否则归属指定项目',
    `item_key`    VARCHAR(128) NOT NULL COMMENT '环境键，如 DB_HOST / DB_PASSWORD',
    `item_value`  MEDIUMTEXT   NOT NULL COMMENT '环境信息原文，可为空字符串或任意多行文本',
    `tags`        VARCHAR(512) DEFAULT NULL COMMENT '逗号分隔的信息分类标签',
    `description` VARCHAR(1024) DEFAULT NULL,
    `owner_id`    BIGINT GENERATED ALWAYS AS (IFNULL(`project_id`, 0)) STORED,
    `deleted`     BIGINT       NOT NULL DEFAULT 0 COMMENT '0 表示有效；删除时写入自身 ID，允许同键再次创建',
    `created_at`  DATETIME(3)  NOT NULL,
    `updated_at`  DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_environment_owner_key` (`owner_id`, `item_key`, `deleted`),
    KEY `idx_environment_project` (`project_id`, `deleted`),
    CONSTRAINT `fk_environment_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务执行环境信息';
