-- 一台部署服务器可以服务多个项目。
--
-- 服务器保留唯一一份连接信息，项目归属迁到关联表；部署配置仍只归属一个项目，
-- 创建或编辑部署时通过关联表判断所选服务器是否可用于该项目。

CREATE TABLE `deployment_server_project` (
    `server_id`  BIGINT      NOT NULL,
    `project_id` BIGINT      NOT NULL,
    `created_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`server_id`, `project_id`),
    KEY `idx_deployment_server_project_project` (`project_id`, `server_id`),
    CONSTRAINT `fk_deployment_server_project_server`
        FOREIGN KEY (`server_id`) REFERENCES `deployment_server` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_deployment_server_project_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '部署服务器可用的项目';

INSERT INTO `deployment_server_project` (`server_id`, `project_id`, `created_at`)
SELECT `id`, `project_id`, UTC_TIMESTAMP(3)
FROM `deployment_server`;

ALTER TABLE `deployment_server`
    DROP FOREIGN KEY `fk_deployment_server_project`,
    DROP INDEX `idx_deployment_server_project`,
    DROP COLUMN `project_id`;
