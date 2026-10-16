-- 平台级执行设置。采用单行表而不是 key-value：
-- 当前配置项少、类型明确，列能直接约束取值范围；将来真正出现任意扩展配置时再拆。
-- 不预置行：首次启动沿用配置文件的 max-concurrency 默认值，界面保存后再持久化覆盖。
CREATE TABLE `execution_setting` (
    `id`                   TINYINT     NOT NULL COMMENT '固定为 1，保证只有一行',
    `max_concurrent_tasks` INT         NOT NULL COMMENT '同时运行的任务数上限',
    `updated_at`           DATETIME(3) NOT NULL COMMENT '最后修改时间（UTC）',
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_execution_setting_single_row` CHECK (`id` = 1),
    CONSTRAINT `chk_execution_setting_concurrency` CHECK (`max_concurrent_tasks` BETWEEN 1 AND 64)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '平台级执行设置';
