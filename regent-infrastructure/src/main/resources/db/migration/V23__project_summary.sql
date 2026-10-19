-- 项目注册后由 Codex 只读分析工作区，生成供后续需求拆解与人工理解使用的项目资料。
-- 不修改历史迁移；已有项目保持 NOT_GENERATED，可在项目详情里按需生成。
ALTER TABLE `project`
    ADD COLUMN `summary_status` VARCHAR(16) NOT NULL DEFAULT 'NOT_GENERATED'
        COMMENT 'NOT_GENERATED/GENERATING/READY/FAILED',
    ADD COLUMN `summary` MEDIUMTEXT DEFAULT NULL COMMENT 'Codex 生成的 Markdown 项目资料',
    ADD COLUMN `summary_error` VARCHAR(1024) DEFAULT NULL COMMENT '最近一次生成失败原因',
    ADD COLUMN `summary_generated_at` DATETIME(3) DEFAULT NULL COMMENT '最近一次成功生成时间（UTC）';
