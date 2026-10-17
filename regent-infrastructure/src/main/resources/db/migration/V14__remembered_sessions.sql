-- 保持登录：普通登录仍是浏览器会话，勾选后使用持久 Cookie 并延长服务端会话。
ALTER TABLE `auth_session`
    ADD COLUMN `remembered` BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '是否长期保持登录' AFTER `expires_at`;
