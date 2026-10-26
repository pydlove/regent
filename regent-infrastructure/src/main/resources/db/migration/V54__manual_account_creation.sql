-- 管理员手动创建账号时下发一次性初始密码，首次登录必须先修改密码。
ALTER TABLE `app_user`
    ADD COLUMN `must_change_password` TINYINT(1) NOT NULL DEFAULT 0
        COMMENT '1=首次登录必须修改密码' AFTER `password_hash`;
