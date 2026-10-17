-- 可复用插件库。任务绑定 plugin_version，而不是跟随插件当前版本，
-- 避免插件升级后悄悄改变已创建任务的执行输入。
CREATE TABLE `plugin` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `name`             VARCHAR(128) NOT NULL,
    `description`      VARCHAR(1024) DEFAULT NULL,
    `tags`             VARCHAR(512) DEFAULT NULL COMMENT '逗号分隔的分类标签',
    `enabled`          TINYINT(1)   NOT NULL DEFAULT 1,
    `current_revision` INT          NOT NULL,
    `created_at`       DATETIME(3)  NOT NULL,
    `updated_at`       DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plugin_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '可复用能力插件';

CREATE TABLE `plugin_version` (
    `plugin_id`    BIGINT       NOT NULL,
    `revision`     INT          NOT NULL,
    `filename`     VARCHAR(255) NOT NULL,
    `content`      MEDIUMTEXT   NOT NULL,
    `content_hash` CHAR(64)     NOT NULL COMMENT 'UTF-8 内容的 SHA-256',
    `created_at`   DATETIME(3)  NOT NULL,
    PRIMARY KEY (`plugin_id`, `revision`),
    CONSTRAINT `fk_plugin_version_plugin`
        FOREIGN KEY (`plugin_id`) REFERENCES `plugin` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '插件不可变内容版本';

CREATE TABLE `task_plugin` (
    `task_id`   BIGINT      NOT NULL,
    `plugin_id` BIGINT      NOT NULL,
    `revision`  INT         NOT NULL,
    `bound_at`  DATETIME(3) NOT NULL,
    PRIMARY KEY (`task_id`, `plugin_id`),
    KEY `idx_task_plugin_version` (`plugin_id`, `revision`),
    CONSTRAINT `fk_task_plugin_task`
        FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_task_plugin_version`
        FOREIGN KEY (`plugin_id`, `revision`) REFERENCES `plugin_version` (`plugin_id`, `revision`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务与插件固定版本绑定';
