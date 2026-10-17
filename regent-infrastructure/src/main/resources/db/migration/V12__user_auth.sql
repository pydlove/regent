-- 平台账号与登录会话。
-- 密码只保存 PBKDF2 编码结果；会话 Cookie 的明文令牌不落库，只保存 SHA-256。
CREATE TABLE `app_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(32)  NOT NULL COMMENT '规范化后的登录名，统一小写',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'PBKDF2-HMAC-SHA256 编码结果',
    `created_at`    DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '平台用户';

CREATE TABLE `auth_session` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT      NOT NULL,
    `token_hash` CHAR(64)    NOT NULL COMMENT 'Cookie 明文令牌的 SHA-256，绝不保存明文',
    `expires_at` DATETIME(3) NOT NULL,
    `created_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_auth_session_token` (`token_hash`),
    KEY `idx_auth_session_user` (`user_id`, `expires_at`),
    CONSTRAINT `fk_auth_session_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '登录会话';
