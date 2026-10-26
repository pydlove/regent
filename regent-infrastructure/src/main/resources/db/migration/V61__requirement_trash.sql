-- 需求回收站：逻辑删除时间与操作人，便于恢复和审计。
ALTER TABLE `requirement`
    ADD COLUMN `deleted_at` DATETIME(3) DEFAULT NULL AFTER `deleted`,
    ADD COLUMN `deleted_by` BIGINT DEFAULT NULL AFTER `deleted_at`,
    ADD KEY `idx_requirement_trash` (`project_id`, `deleted`, `deleted_at`),
    ADD CONSTRAINT `fk_requirement_deleted_by`
        FOREIGN KEY (`deleted_by`) REFERENCES `app_user` (`id`);
