-- 需求澄清、任务拆解草稿与任务依赖。

ALTER TABLE `requirement`
    ADD COLUMN `acceptance_text` MEDIUMTEXT DEFAULT NULL AFTER `raw_text`,
    ADD COLUMN `clarification_text` MEDIUMTEXT DEFAULT NULL AFTER `acceptance_text`,
    ADD COLUMN `clarification_questions` JSON DEFAULT NULL AFTER `clarification_text`,
    ADD COLUMN `assumptions` JSON DEFAULT NULL AFTER `clarification_questions`,
    ADD COLUMN `task_drafts` JSON DEFAULT NULL AFTER `assumptions`,
    ADD COLUMN `clarified_at` DATETIME(3) DEFAULT NULL AFTER `planned_at`;

CREATE TABLE `task_dependency` (
    `task_id`         BIGINT      NOT NULL,
    `depends_on_task_id` BIGINT   NOT NULL,
    `created_at`      DATETIME(3) NOT NULL,
    PRIMARY KEY (`task_id`, `depends_on_task_id`),
    KEY `idx_task_dependency_depends_on` (`depends_on_task_id`, `task_id`),
    CONSTRAINT `fk_task_dependency_task`
        FOREIGN KEY (`task_id`) REFERENCES `task` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_task_dependency_depends_on`
        FOREIGN KEY (`depends_on_task_id`) REFERENCES `task` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `chk_task_dependency_self`
        CHECK (`task_id` <> `depends_on_task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '需求拆解任务之间的完成依赖';
