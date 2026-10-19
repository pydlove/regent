-- 用途说明收敛为更短的展示名称，并增加“密码值”标记。
-- 旧数据超长时只保留前 64 个字符；空名称用环境键补一个可读名称，避免升级后无法展示。
UPDATE `environment_entry`
SET `description` = LEFT(`description`, 64)
WHERE `description` IS NOT NULL AND CHAR_LENGTH(`description`) > 64;

UPDATE `environment_entry`
SET `description` = LEFT(CONCAT('环境信息 ', `item_key`), 64)
WHERE `description` IS NULL OR TRIM(`description`) = '';

ALTER TABLE `environment_entry`
    MODIFY COLUMN `description` VARCHAR(64) NOT NULL COMMENT '环境信息名称，最长 64 字符',
    ADD COLUMN `secret` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 表示密码值，列表和详情默认脱敏'
        AFTER `description`;
