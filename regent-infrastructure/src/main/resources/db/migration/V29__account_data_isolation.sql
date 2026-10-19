-- 账户数据隔离。
--
-- 归属规则：
--   * project 是业务数据的租户根，任务、决策、缺陷、部署、测试用例都通过 project_id 继承归属；
--   * environment_entry 可以为全局（project_id 为空），所以必须单独记 user_id；
--   * plan 本身不属于项目（它只引用任务），也单独记 user_id。
--
-- 存量数据统一归给最早创建的账号。迁移执行时还没有任何账号、也没有业务数据时，
-- 这些 UPDATE 不会写入任何行，新数据由应用在写入时带上当前登录用户。

ALTER TABLE `project`
    ADD COLUMN `owner_id` BIGINT DEFAULT NULL COMMENT '项目归属账号' AFTER `id`,
    ADD KEY `idx_project_owner` (`owner_id`, `deleted`, `id`),
    ADD CONSTRAINT `fk_project_owner`
        FOREIGN KEY (`owner_id`) REFERENCES `app_user` (`id`);

UPDATE `project`
SET `owner_id` = (SELECT MIN(`id`) FROM `app_user`)
WHERE `owner_id` IS NULL;

ALTER TABLE `environment_entry`
    ADD COLUMN `user_id` BIGINT DEFAULT NULL COMMENT '环境信息归属账号' AFTER `id`,
    ADD KEY `idx_environment_user` (`user_id`, `deleted`),
    ADD CONSTRAINT `fk_environment_user`
        FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`);

UPDATE `environment_entry`
SET `user_id` = (SELECT MIN(`id`) FROM `app_user`)
WHERE `user_id` IS NULL;

-- 原来只按项目/全局区分键，现在必须先按账号区分，否则两个账号不能各自拥有 ENV_001。
ALTER TABLE `environment_entry`
    DROP INDEX `uk_environment_owner_key`,
    ADD UNIQUE KEY `uk_environment_user_owner_key`
        (`user_id`, `owner_id`, `item_key`, `deleted`);

ALTER TABLE `plan`
    ADD COLUMN `user_id` BIGINT DEFAULT NULL COMMENT '计划归属账号' AFTER `id`,
    ADD KEY `idx_plan_user` (`user_id`, `created_at`),
    ADD CONSTRAINT `fk_plan_user`
        FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`);

UPDATE `plan`
SET `user_id` = (SELECT MIN(`id`) FROM `app_user`)
WHERE `user_id` IS NULL;

-- 老账号保存的草稿可能引用了刚迁移给老账号的项目。草稿本身属于另一个账号时，
-- 清掉项目引用，避免新账号在新建任务弹框里继续看到一个自己无权访问的项目。
UPDATE `task_draft` d
    JOIN `project` p ON p.`id` = d.`project_id`
SET d.`project_id` = NULL
WHERE d.`user_id` <> p.`owner_id`;
