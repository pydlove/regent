-- =====================================================================================
-- V38：设备代理实例重启恢复
--
-- device_id 标识一台机器，agent_instance_id 标识一次设备代理进程。设备进程重启后实例号变化，
-- 平台据此立即收敛该设备遗留的活跃租约并重新排队；同一进程网络重连时实例号不变，不会被误判。
-- =====================================================================================

ALTER TABLE `execution_device`
    ADD COLUMN `agent_instance_id` VARCHAR(64) DEFAULT NULL
        COMMENT '设备代理进程实例 UUID；每次进程启动重新生成' AFTER `executor`,
    ADD KEY `idx_execution_device_instance` (`agent_instance_id`);
