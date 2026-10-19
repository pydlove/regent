-- 服务器级默认部署目录。
--
-- 按平台规范编写的脚本只读取 REGENT_DEPLOY_DIRECTORY，不在源码里写死路径；
-- 因此这个值不能从脚本反推，应该由服务器/环境配置保存一次，新建部署时自动带出。
ALTER TABLE `deployment_server`
    ADD COLUMN `default_remote_directory` VARCHAR(512) DEFAULT NULL
        COMMENT '新建部署时默认带出的远程工作目录' AFTER `auth_type`;
