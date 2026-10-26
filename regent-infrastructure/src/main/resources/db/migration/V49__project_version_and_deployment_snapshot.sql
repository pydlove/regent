ALTER TABLE `project`
    ADD COLUMN `version_source` VARCHAR(16) NOT NULL DEFAULT 'GIT_TAG'
        COMMENT 'GIT_TAG/MANUAL' AFTER `default_decision_level`,
    ADD COLUMN `fallback_version` VARCHAR(64) DEFAULT NULL
        COMMENT 'Git Tag 不可用时使用；MANUAL 模式必填' AFTER `version_source`;

ALTER TABLE `deployment_run`
    ADD COLUMN `project_version` VARCHAR(64) DEFAULT NULL
        COMMENT '本次部署解析出的项目版本快照' AFTER `review_decision_id`,
    ADD COLUMN `version_source` VARCHAR(16) DEFAULT NULL
        COMMENT 'GIT_TAG/MANUAL/FALLBACK' AFTER `project_version`,
    ADD KEY `idx_deployment_run_project_version` (`project_id`, `project_version`);
