-- =====================================================================================
-- V2：给执行记录补「正在跑」这个状态，并落下改动文件清单
--
-- 1) task_run.status 在 V1 的取值注释里只有 SUCCESS/FAILED/BLOCKED/TIMEOUT。但 run_event 的
--    外键指向 task_run，想让「正在跑」的日志有挂靠点，就必须在进程还在跑的时候先落一行 ——
--    那一行的状态只能是 RUNNING。V1 已经执行过，按铁律 G 只能追加修正，不能回头改它。
--    这里只改列注释（类型、唯一键都不动），因为状态值本来就是 VARCHAR 存的。
--
-- 2) changed_files：仲裁的路径通配规则要用它（改到 deploy/ 下就必须升级），事后复盘也要看
--    「这一次到底改了什么」。少了这一列，那份清单只在内存里活过一次，决策单落库之后
--    就再也说不出当时匹配的到底是哪些文件。
-- =====================================================================================

ALTER TABLE `task_run`
    MODIFY COLUMN `status` VARCHAR(16) NOT NULL
        COMMENT 'RUNNING/SUCCESS/FAILED/BLOCKED/TIMEOUT';

ALTER TABLE `task_run`
    ADD COLUMN `changed_files` JSON DEFAULT NULL COMMENT '本次改动的文件（相对项目根），用于路径通配规则与复盘' AFTER `schema_valid`;
