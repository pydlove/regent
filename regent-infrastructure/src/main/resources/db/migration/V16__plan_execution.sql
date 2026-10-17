-- 计划是一次性的任务批次：到点后冻结任务集合与标题，执行完成后保留逐项结果和总结。
CREATE TABLE `plan` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `name`          VARCHAR(128) NOT NULL,
    `status`        VARCHAR(16)  NOT NULL COMMENT 'SCHEDULED/RUNNING/COMPLETED',
    `scheduled_at`  DATETIME(3)  NOT NULL COMMENT '计划到点时间',
    `started_at`    DATETIME(3)  DEFAULT NULL,
    `finished_at`   DATETIME(3)  DEFAULT NULL,
    `created_at`    DATETIME(3)  NOT NULL,
    `updated_at`    DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_plan_due` (`status`, `scheduled_at`),
    KEY `idx_plan_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务计划';

CREATE TABLE `plan_task` (
    `plan_id`     BIGINT      NOT NULL,
    `task_id`     BIGINT      NOT NULL,
    `sort_order`  INT         NOT NULL COMMENT '计划内展示与派发顺序，从 0 开始',
    `created_at`  DATETIME(3) NOT NULL,
    PRIMARY KEY (`plan_id`, `task_id`),
    KEY `idx_plan_task_task` (`task_id`),
    CONSTRAINT `fk_plan_task_plan` FOREIGN KEY (`plan_id`) REFERENCES `plan` (`id`),
    CONSTRAINT `fk_plan_task_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '计划纳管任务';

CREATE TABLE `plan_run` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `plan_id`          BIGINT       NOT NULL,
    `plan_name`        VARCHAR(128) NOT NULL COMMENT '开跑时的计划名称快照',
    `trigger_type`     VARCHAR(16)  NOT NULL COMMENT 'SCHEDULED/MANUAL',
    `status`           VARCHAR(16)  NOT NULL COMMENT 'RUNNING/COMPLETED/PARTIAL/FAILED',
    `task_count`       INT          NOT NULL,
    `progress_count`   INT          NOT NULL DEFAULT 0,
    `succeeded_count`  INT          NOT NULL DEFAULT 0,
    `failed_count`     INT          NOT NULL DEFAULT 0,
    `cancelled_count`  INT          NOT NULL DEFAULT 0,
    `skipped_count`    INT          NOT NULL DEFAULT 0,
    `running_count`    INT          NOT NULL DEFAULT 0,
    `blocked_count`    INT          NOT NULL DEFAULT 0,
    `summary`          MEDIUMTEXT   DEFAULT NULL,
    `started_at`       DATETIME(3)  NOT NULL,
    `finished_at`      DATETIME(3)  DEFAULT NULL,
    `created_at`       DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_plan_run_plan` (`plan_id`, `id`),
    KEY `idx_plan_run_running` (`status`, `id`),
    CONSTRAINT `fk_plan_run_plan` FOREIGN KEY (`plan_id`) REFERENCES `plan` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '计划执行记录';

CREATE TABLE `plan_run_item` (
    `plan_run_id` BIGINT       NOT NULL,
    `task_id`     BIGINT       NOT NULL,
    `task_title`  VARCHAR(256) NOT NULL COMMENT '开跑时的任务标题快照',
    `status`      VARCHAR(16)  NOT NULL COMMENT 'PENDING/RUNNING/BLOCKED/SUCCEEDED/FAILED/CANCELLED/SKIPPED',
    `detail`      VARCHAR(512) DEFAULT NULL COMMENT '最终摘要或跳过原因',
    `updated_at`  DATETIME(3)  NOT NULL,
    PRIMARY KEY (`plan_run_id`, `task_id`),
    KEY `idx_plan_run_item_task` (`task_id`),
    CONSTRAINT `fk_plan_run_item_run` FOREIGN KEY (`plan_run_id`) REFERENCES `plan_run` (`id`),
    CONSTRAINT `fk_plan_run_item_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '计划逐任务执行结果';
