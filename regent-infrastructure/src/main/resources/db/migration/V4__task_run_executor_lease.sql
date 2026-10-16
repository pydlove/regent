-- =====================================================================================
-- V4：外部执行者
--
-- 平台原先只有一种执行者：自己拉起的本机 codex exec。V4 起，codex 桌面版、别的 CLI、
-- 别的模型工具也能从 /api/agent/* 认领任务并把结果报回来。多出来的三个问题都要落列：
--
-- 1) 这条记录是谁跑的 —— executor + agent_name。出问题时能回答「这行是谁留下的」，
--    界面上也能一眼看出「任务在跑，但跑它的不是平台自己」；
-- 2) 它还活着吗 —— lease_expires_at。外部执行者在平台看不见的进程里跑，平台杀不掉它，
--    只能靠心跳续租；租约到期即视为失联，由 AgentLeaseJob 判失败并留痕；
-- 3) 工作区是不是被占着 —— idx_run_lease 让「这个项目上还有没有活跃租约」是一次索引查询。
--    理由与 execution.max-concurrency 默认 1 相同：一条执行链路独占整个工作区。
--
-- 默认值 'codex-cli' 让 V4 之前的历史行自动落位，不需要数据回填。
-- =====================================================================================

ALTER TABLE `task_run`
    ADD COLUMN `executor` VARCHAR(32) NOT NULL DEFAULT 'codex-cli'
        COMMENT '执行这条记录的人：codex-cli=平台自己拉起的本机 codex；agent=外部执行者' AFTER `branch_name`,
    ADD COLUMN `agent_name` VARCHAR(128) DEFAULT NULL
        COMMENT '外部执行者的自称，仅用于显示与追责；内部执行者为 NULL' AFTER `executor`,
    ADD COLUMN `lease_expires_at` DATETIME(3) DEFAULT NULL
        COMMENT '外部执行者的租约到期时间；到期即视为失联，由回收任务收场' AFTER `agent_name`,
    ADD KEY `idx_run_lease` (`executor`, `lease_expires_at`);
