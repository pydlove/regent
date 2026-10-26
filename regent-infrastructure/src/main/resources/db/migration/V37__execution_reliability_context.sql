-- =====================================================================================
-- V37：执行可靠性中心
--
-- 1) execution_device 持久化设备心跳，平台不再只依赖接口层内存判断设备是否在线；
-- 2) task_run 记录设备标识、最后心跳与最后事件时间，控制塔可以据此推导健康状态；
-- 3) active_task_id 是生成列 + 唯一索引，从数据库层保证同一任务最多一条 RUNNING 记录。
--    这是重复心跳、并发认领和重试风暴的最后一道幂等边界。
-- =====================================================================================

CREATE TABLE `execution_device` (
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `device_id`             VARCHAR(64)  NOT NULL COMMENT '设备代理生成的稳定 UUID',
    `owner_id`              BIGINT       NOT NULL COMMENT '设备归属账号',
    `hostname`              VARCHAR(255) NOT NULL,
    `platform`              VARCHAR(64)  NOT NULL,
    `executor`              VARCHAR(128) NOT NULL,
    `codex_available`       TINYINT(1)   NOT NULL DEFAULT 0,
    `codex_executable_path` VARCHAR(1024) DEFAULT NULL,
    `codex_version`         VARCHAR(255) DEFAULT NULL,
    `codex_message`         VARCHAR(512) DEFAULT NULL,
    `codex_checked_at`      DATETIME(3)  DEFAULT NULL,
    `client_release`        VARCHAR(32)  DEFAULT NULL COMMENT '桌面壳发行码，如 R100B002',
    `shell_release`         VARCHAR(32)  DEFAULT NULL COMMENT '客户端壳发行码',
    `last_seen_at`          DATETIME(3)  NOT NULL,
    `last_claimed_at`       DATETIME(3)  DEFAULT NULL,
    `created_at`            DATETIME(3)  NOT NULL,
    `updated_at`            DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_execution_device_id` (`device_id`),
    KEY `idx_execution_device_owner_seen` (`owner_id`, `last_seen_at`),
    CONSTRAINT `fk_execution_device_owner` FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '执行设备心跳';


ALTER TABLE `task_run`
    ADD COLUMN `device_id` VARCHAR(64) DEFAULT NULL
        COMMENT '认领这次执行的设备 UUID；通用外部执行者可不填' AFTER `agent_name`,
    ADD COLUMN `last_heartbeat_at` DATETIME(3) DEFAULT NULL
        COMMENT '外部执行者最后一次续租时间' AFTER `lease_expires_at`,
    ADD COLUMN `last_event_at` DATETIME(3) DEFAULT NULL
        COMMENT '这次执行最后一条事件时间，用于卡住检测' AFTER `last_heartbeat_at`,
    ADD KEY `idx_run_device_lease` (`device_id`, `lease_expires_at`);


-- 历史环境若已经因并发认领留下同一任务的多条 RUNNING，先保留最后一条，
-- 其余收敛为 INTERRUPTED，否则下面的唯一约束无法安全建立。
UPDATE `task_run` r
JOIN (
    SELECT `task_id`, MAX(`attempt_no`) AS `keep_attempt`
    FROM `task_run`
    WHERE `status` = 'RUNNING'
    GROUP BY `task_id`
    HAVING COUNT(*) > 1
) duplicate_active ON duplicate_active.`task_id` = r.`task_id`
SET r.`status` = 'INTERRUPTED',
    r.`summary` = COALESCE(
        r.`summary`,
        'V37 迁移前发现重复活跃执行，平台保留最后一条并中断其余记录'
    ),
    r.`finished_at` = COALESCE(r.`finished_at`, UTC_TIMESTAMP(3)),
    r.`lease_expires_at` = NULL
WHERE r.`status` = 'RUNNING'
  AND r.`attempt_no` < duplicate_active.`keep_attempt`;


UPDATE `task_run` r
LEFT JOIN (
    SELECT `task_run_id`, MAX(`occurred_at`) AS `last_event_at`
    FROM `run_event`
    GROUP BY `task_run_id`
) events ON events.`task_run_id` = r.`id`
SET r.`last_event_at` = events.`last_event_at`
WHERE events.`last_event_at` IS NOT NULL;


ALTER TABLE `task_run`
    ADD COLUMN `active_task_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'RUNNING' THEN `task_id` ELSE NULL END
        ) STORED COMMENT 'RUNNING 记录的任务号；唯一索引保证单任务单活跃执行',
    ADD UNIQUE KEY `uk_run_active_task` (`active_task_id`);
