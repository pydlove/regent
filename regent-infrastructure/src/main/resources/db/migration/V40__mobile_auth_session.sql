-- 手机 App 使用独立 Bearer 会话，不复用浏览器 Cookie，也不保存任何明文令牌。
CREATE TABLE `mobile_auth_session` (
    `id`                          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`                     BIGINT       NOT NULL,
    `installation_id`             VARCHAR(128) NOT NULL COMMENT 'App 安装实例稳定标识',
    `access_token_hash`           CHAR(64)     NOT NULL COMMENT '访问令牌 SHA-256',
    `refresh_token_hash`          CHAR(64)     NOT NULL COMMENT '当前刷新令牌 SHA-256',
    `previous_refresh_token_hash` CHAR(64)     NULL COMMENT '上一枚刷新令牌 SHA-256，用于重放检测',
    `access_expires_at`           DATETIME(3)  NOT NULL,
    `refresh_expires_at`          DATETIME(3)  NOT NULL,
    `created_at`                  DATETIME(3)  NOT NULL,
    `revoked_at`                  DATETIME(3)  NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mobile_auth_access` (`access_token_hash`),
    UNIQUE KEY `uk_mobile_auth_refresh` (`refresh_token_hash`),
    KEY `idx_mobile_auth_installation` (`user_id`, `installation_id`, `revoked_at`),
    KEY `idx_mobile_auth_previous_refresh` (`previous_refresh_token_hash`),
    CONSTRAINT `fk_mobile_auth_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '手机 App 独立登录会话';
