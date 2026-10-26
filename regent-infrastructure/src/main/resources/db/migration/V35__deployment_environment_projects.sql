-- 部署与环境信息都可以归属多个项目。
--
-- 原 project_id 继续保留为“主项目”，用于执行时的工作区与环境键归属；
-- 项目关联表负责完整的多项目归属和列表筛选。存量数据自动迁移为主项目关联。

CREATE TABLE `deployment_project` (
    `deployment_id` BIGINT      NOT NULL,
    `project_id`    BIGINT      NOT NULL,
    `created_at`    DATETIME(3) NOT NULL,
    PRIMARY KEY (`deployment_id`, `project_id`),
    KEY `idx_deployment_project_project` (`project_id`, `deployment_id`),
    CONSTRAINT `fk_deployment_project_deployment`
        FOREIGN KEY (`deployment_id`) REFERENCES `deployment` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_deployment_project_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '部署可归属的项目';

INSERT INTO `deployment_project` (`deployment_id`, `project_id`, `created_at`)
SELECT `id`, `project_id`, UTC_TIMESTAMP(3)
FROM `deployment`;

CREATE TABLE `environment_entry_project` (
    `environment_id` BIGINT      NOT NULL,
    `project_id`     BIGINT      NOT NULL,
    `created_at`     DATETIME(3) NOT NULL,
    PRIMARY KEY (`environment_id`, `project_id`),
    KEY `idx_environment_entry_project_project` (`project_id`, `environment_id`),
    CONSTRAINT `fk_environment_entry_project_entry`
        FOREIGN KEY (`environment_id`) REFERENCES `environment_entry` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_environment_entry_project_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '环境信息可归属的项目；空关联表示全局';

INSERT INTO `environment_entry_project` (`environment_id`, `project_id`, `created_at`)
SELECT `id`, `project_id`, UTC_TIMESTAMP(3)
FROM `environment_entry`
WHERE `project_id` IS NOT NULL;
