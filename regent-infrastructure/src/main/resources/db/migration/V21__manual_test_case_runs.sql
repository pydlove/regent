-- -------------------------------------------------------------------------------------
-- 测试用例支持菜单手动执行。
--
-- 自动执行来自任务完成，手动执行没有任务号；两者都必须在记录里保留项目归属，
-- 否则平台重启或测试用例删除后就找不到执行目录。
-- -------------------------------------------------------------------------------------
ALTER TABLE `test_case_run`
    ADD COLUMN `project_id` BIGINT DEFAULT NULL AFTER `id`,
    ADD COLUMN `trigger_type` VARCHAR(16) NOT NULL DEFAULT 'TASK_COMPLETION' AFTER `task_id`;

UPDATE `test_case_run` run
JOIN `test_case` test_case ON test_case.`id` = run.`test_case_id`
SET run.`project_id` = test_case.`project_id`
WHERE run.`project_id` IS NULL;

ALTER TABLE `test_case_run`
    MODIFY COLUMN `project_id` BIGINT NOT NULL COMMENT '执行所属项目；手动执行没有 task_id 时靠它定位工作区',
    MODIFY COLUMN `task_id` BIGINT DEFAULT NULL COMMENT '任务完成触发时的任务号；手动执行为 NULL',
    ADD KEY `idx_test_run_project` (`project_id`, `id`),
    ADD CONSTRAINT `fk_test_run_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`);
