-- 内置插件用稳定 key 与发行包同步。key 只由发行包写入，手工插件保持 NULL。
ALTER TABLE `plugin`
    ADD COLUMN `builtin_key` VARCHAR(64) DEFAULT NULL COMMENT '内置插件稳定标识' AFTER `id`,
    ADD UNIQUE KEY `uk_plugin_builtin_key` (`builtin_key`);
