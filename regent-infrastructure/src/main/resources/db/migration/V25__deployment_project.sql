-- 部署配置必须归属一个项目。V24 已上线过的环境在升级前如果已有部署行，需要先明确归属，
-- 因此这里直接加 NOT NULL；迁移会失败而不是替用户猜一个项目。
ALTER TABLE `deployment`
    ADD COLUMN `project_id` BIGINT NOT NULL AFTER `id`,
    ADD KEY `idx_deployment_project` (`project_id`, `deleted`, `updated_at`),
    ADD CONSTRAINT `fk_deployment_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`);

ALTER TABLE `deployment_run`
    ADD COLUMN `project_id` BIGINT NOT NULL AFTER `deployment_id`,
    ADD KEY `idx_deployment_run_project` (`project_id`, `id`),
    ADD CONSTRAINT `fk_deployment_run_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`);
