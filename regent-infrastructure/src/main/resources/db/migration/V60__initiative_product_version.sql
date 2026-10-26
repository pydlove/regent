-- 版本级立项：每个产品版本独立审批自己的目标、范围和资源。
ALTER TABLE `initiative`
    ADD COLUMN `version_id` BIGINT DEFAULT NULL AFTER `project_id`,
    ADD UNIQUE KEY `uk_initiative_product_version` (`version_id`),
    ADD CONSTRAINT `fk_initiative_product_version`
        FOREIGN KEY (`version_id`) REFERENCES `product_version` (`id`);
