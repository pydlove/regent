-- 用户公开资料。登录名与邮箱仍由认证流程维护，这里只增加可编辑的显示名和头像元数据。
-- 头像文件走 regent.attachments.dir，与任务/Bug 图片共用白名单和存储边界；表中不保存图片字节。
ALTER TABLE `app_user`
    ADD COLUMN `display_name` VARCHAR(64) NULL
        COMMENT '对外展示名称' AFTER `username`,
    ADD COLUMN `avatar_filename` VARCHAR(255) NULL
        COMMENT '用户上传时的原文件名，仅用于展示' AFTER `password_hash`,
    ADD COLUMN `avatar_content_type` VARCHAR(128) NULL
        COMMENT 'image/png 等白名单类型' AFTER `avatar_filename`,
    ADD COLUMN `avatar_storage_path` VARCHAR(512) NULL
        COMMENT '平台侧绝对路径，不通过 API 暴露' AFTER `avatar_content_type`,
    ADD COLUMN `avatar_updated_at` DATETIME(3) NULL
        COMMENT '头像替换时间（UTC），同时用于图片缓存版本' AFTER `avatar_storage_path`;

UPDATE `app_user`
SET `display_name` = `username`
WHERE `display_name` IS NULL;

ALTER TABLE `app_user`
    MODIFY COLUMN `display_name` VARCHAR(64) NOT NULL COMMENT '对外展示名称',
    ADD CONSTRAINT `chk_app_user_avatar_complete` CHECK (
        (`avatar_filename` IS NULL
            AND `avatar_content_type` IS NULL
            AND `avatar_storage_path` IS NULL
            AND `avatar_updated_at` IS NULL)
        OR
        (`avatar_filename` IS NOT NULL
            AND `avatar_content_type` IS NOT NULL
            AND `avatar_storage_path` IS NOT NULL
            AND `avatar_updated_at` IS NOT NULL)
    );
