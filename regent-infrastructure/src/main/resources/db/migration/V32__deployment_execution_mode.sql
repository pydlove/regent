-- 支持在 Regent 所在机器直接执行项目发布脚本。
--
-- REMOTE_SCRIPT 沿用原有行为：把脚本文本通过 SSH stdin 送到远端执行。
-- PROJECT_SCRIPT 从项目根目录执行指定脚本，由脚本自行完成构建、上传和重启。

ALTER TABLE `deployment`
    ADD COLUMN `execution_mode` VARCHAR(24) NOT NULL DEFAULT 'REMOTE_SCRIPT'
        COMMENT 'REMOTE_SCRIPT/PROJECT_SCRIPT' AFTER `script_hash`,
    ADD COLUMN `project_script_path` VARCHAR(512) DEFAULT NULL
        COMMENT '项目内发布脚本的相对路径' AFTER `execution_mode`;

-- 现有 Regent 双模式脚本使用显式 -H/--host 区分本地完整发布与远端执行。
-- 按内容识别后迁移，避免历史配置继续被塞进远端 bash -s。
UPDATE `deployment`
SET `execution_mode` = 'PROJECT_SCRIPT',
    `project_script_path` = 'deploy/remote/deploy.sh'
WHERE `script_filename` = 'deploy.sh'
  AND `script_content` LIKE '%deploy-local.sh%'
  AND `script_content` LIKE '%REGENT_SERVER_IP%';

ALTER TABLE `deployment_run`
    ADD COLUMN `execution_mode` VARCHAR(24) NOT NULL DEFAULT 'REMOTE_SCRIPT'
        COMMENT 'REMOTE_SCRIPT/PROJECT_SCRIPT' AFTER `script_hash`,
    ADD COLUMN `project_script_path` VARCHAR(512) DEFAULT NULL
        COMMENT '项目内发布脚本的相对路径' AFTER `execution_mode`;
