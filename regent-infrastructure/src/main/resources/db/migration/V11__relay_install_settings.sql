-- 中继安装连接信息。SSH 密码不进入数据库，只在用户点击安装时随请求使用一次。
ALTER TABLE `relay_setting`
    ADD COLUMN `server_ip` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '中继服务器 IP 或主机名' AFTER `server_url`,
    ADD COLUMN `ssh_username` VARCHAR(32) NOT NULL DEFAULT 'root' COMMENT 'SSH 登录用户名' AFTER `server_ip`,
    ADD COLUMN `install_directory` VARCHAR(512) NOT NULL DEFAULT '/root/app/regent-relay' COMMENT '服务器安装目录' AFTER `ssh_username`;
