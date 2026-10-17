-- 中继状态监听的平台设置。采用单行表，只保存公开服务器地址与监听开关；
-- 连接器共享密钥仍由本机 ~/.regent/relay-connector.env 管理，绝不进入数据库。
CREATE TABLE `relay_setting` (
    `id`                 TINYINT       NOT NULL COMMENT '固定为 1，保证只有一行',
    `server_url`         VARCHAR(2048) NOT NULL COMMENT '中继服务器站点根地址',
    `monitoring_enabled` TINYINT(1)    NOT NULL COMMENT '是否在设置页监听中继状态',
    `updated_at`         DATETIME(3)   NOT NULL COMMENT '最后修改时间（UTC）',
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_relay_setting_single_row` CHECK (`id` = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '中继状态监听设置';
