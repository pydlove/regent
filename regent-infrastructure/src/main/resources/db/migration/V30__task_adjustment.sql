-- 执行期间新增的调整方向。
--
-- 调整不是直接改写 task.prompt：原指令必须保持可追溯，执行期间收到的方向也要按创建顺序
-- 单独下发。source_task_id 只保存来源任务快照的引用；正文与标题另行复制，来源任务之后编辑
-- 或删除不会改变当时交给执行器的内容。

CREATE TABLE `task_adjustment` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`        BIGINT       NOT NULL COMMENT '接收调整的目标任务',
    `source_type`    VARCHAR(16)  NOT NULL COMMENT 'TASK / TEMPORARY',
    `source_task_id` BIGINT       DEFAULT NULL COMMENT '来源任务，仅 TASK 来源非空',
    `source_title`   VARCHAR(256) DEFAULT NULL COMMENT '来源任务标题快照',
    `content`        MEDIUMTEXT   NOT NULL COMMENT '调整正文或来源任务 prompt 快照',
    `status`         VARCHAR(16)  NOT NULL COMMENT 'PENDING / APPLIED',
    `applied_run_id` BIGINT       DEFAULT NULL COMMENT '实际把调整交给执行器的尝试',
    `applied_at`     DATETIME(3)  DEFAULT NULL,
    `created_at`     DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_adjustment_task_status` (`task_id`, `status`, `id`),
    KEY `idx_adjustment_source_task` (`source_task_id`),
    KEY `idx_adjustment_applied_run` (`applied_run_id`),
    CONSTRAINT `fk_adjustment_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_adjustment_source_task` FOREIGN KEY (`source_task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_adjustment_run` FOREIGN KEY (`applied_run_id`) REFERENCES `task_run` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '任务执行期间新增的调整方向';
