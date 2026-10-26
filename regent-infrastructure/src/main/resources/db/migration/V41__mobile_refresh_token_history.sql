-- 保留所有已轮换的刷新令牌摘要。任一枚旧令牌再次出现时，服务端撤销整组手机安装会话。
CREATE TABLE `mobile_refresh_token_history` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT      NOT NULL,
    `token_hash` CHAR(64)    NOT NULL COMMENT '已使用刷新令牌 SHA-256',
    `used_at`    DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mobile_refresh_history_token` (`token_hash`),
    KEY `idx_mobile_refresh_history_session` (`session_id`, `used_at`),
    CONSTRAINT `fk_mobile_refresh_history_session`
        FOREIGN KEY (`session_id`) REFERENCES `mobile_auth_session` (`id`)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '手机刷新令牌重放检测历史';
