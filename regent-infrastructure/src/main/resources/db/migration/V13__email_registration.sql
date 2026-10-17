-- 邮箱注册：历史用户名账号的 email 保持 NULL，不破坏现有登录。
ALTER TABLE `app_user`
    ADD COLUMN `email` VARCHAR(254) NULL COMMENT '规范化后的注册邮箱，统一小写' AFTER `username`,
    ADD UNIQUE KEY `uk_app_user_email` (`email`);

-- 验证码只保存摘要；成功使用、主动重发或连续输错 5 次都会写入 consumed_at。
CREATE TABLE `email_verification_code` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `email`           VARCHAR(254) NOT NULL,
    `code_hash`       CHAR(64)     NOT NULL COMMENT '6 位验证码的 SHA-256，不保存明文',
    `expires_at`      DATETIME(3)  NOT NULL,
    `consumed_at`     DATETIME(3)  NULL,
    `failed_attempts` INT          NOT NULL DEFAULT 0,
    `created_at`      DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_email_code_active` (`email`, `consumed_at`, `expires_at`),
    KEY `idx_email_code_created` (`email`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '邮箱验证码';
