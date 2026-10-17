-- 新分支必须经人工批准后才能创建。历史任务没有待处理申请，默认 NONE。
ALTER TABLE `task`
    ADD COLUMN `branch_approval_state` VARCHAR(16) NOT NULL DEFAULT 'NONE'
        COMMENT 'NONE/PENDING/APPROVED：首次创建任务分支的审批状态'
        AFTER `branch_name`;
