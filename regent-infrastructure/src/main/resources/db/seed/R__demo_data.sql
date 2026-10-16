-- =====================================================================================
-- 演示数据（仅本地开发用；2026-10-16 起默认不加载，本地库保持干净）
--
-- 启用方式：在 application-local.yml 里把 flyway.locations 换成
--     locations: classpath:db/migration,classpath:db/seed
-- 再重启后端即灌入。不想要了就切回 classpath:db/migration，并手工执行下面「第 0 段」的
-- DELETE（默认配置下本文件不在 Flyway 扫描范围里，加载与否完全由那一行决定）。
--
-- 为什么是 R__ 而不是 V2__：
--   * 它不改变表结构，只灌数据。用 V 编号会占用版本号序列，让「结构变更」与「演示数据」
--     挤在同一条时间线上 —— 生产环境永远不执行本文件，历史记录于是与开发库不一致；
--   * R__ 在前缀不变、内容未变时不会重跑；改了内容会重跑（这就是它「可重复」的含义）。
--
-- 幂等策略：先按本文件占用的 ID 段删除，再重建。
--   * 只删自己写的 ID，不动用户在本地新增的数据；
--   * 删除顺序与外键方向相反（子表先删），否则会被外键挡住；
--   * ID 从 1 开始：空库第一次启动就是这套数据，自增游标排在它后面。
--
-- 注意本文件里的项目路径指向**真实仓库**（Regent 自己）：所以本地必须把派发心跳关掉
-- （application-local.yml 的 regent.execution.dispatch.enabled: false），否则启动即开跑。
-- 万一真跑过一轮、任务卡在 RUNNING 上，把本文件随便改一行让它重跑一次即可复位
-- （R__ 只在内容变化时执行，这就是它「可重复」的含义）。
--
-- 时间列统一写 UTC（表约定 DATETIME(3) 存 UTC），所以基准用 UTC_TIMESTAMP(3)。
-- 事件的先后关系由「距现在多少分钟」表达，保证每次重跑看到的都是「刚刚发生」的列表。
-- =====================================================================================

-- ---------------------------------------------------------------------------
-- 0. 清理本文件占用的 ID 段（子表在前）
-- ---------------------------------------------------------------------------
DELETE FROM notification      WHERE id BETWEEN 1 AND 20;
DELETE FROM approval_token    WHERE id BETWEEN 1 AND 10;
-- 决策单号有两段：手写的 1–6，以及给批量阻塞任务生成的大号段（101 起，见第 7 段）。
-- 范围必须覆盖两者，否则删 task 时会被 fk_decision_task 挡住。
DELETE FROM decision          WHERE id BETWEEN 1 AND 999;
DELETE FROM bug               WHERE id BETWEEN 1 AND 20;
DELETE FROM run_event         WHERE task_run_id BETWEEN 1 AND 100;
DELETE FROM task_run          WHERE id BETWEEN 1 AND 100;
DELETE FROM task              WHERE id BETWEEN 1 AND 45;
DELETE FROM requirement       WHERE id BETWEEN 1 AND 5;
DELETE FROM decision_rule     WHERE id BETWEEN 1 AND 10;
DELETE FROM project           WHERE id BETWEEN 1 AND 5;


-- ---------------------------------------------------------------------------
-- 1. project —— 两个项目：一个在开发中，一个已暂停派发
-- ---------------------------------------------------------------------------
INSERT INTO project (id, code, name, path, tech_stack, verify_method, default_decision_level, enabled, deleted, created_at, updated_at)
VALUES
    (1, 'regent', 'Regent 平台', '/Users/panyong/shanjie/Regent', 'Java 17 / Spring Boot 3.5 / Vue 3',
     'mvn -q clean verify', 'L1', 1, 0, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 30 DAY), UTC_TIMESTAMP(3)),
    (2, 'app_audit', '审计平台', '/Users/panyong/shanjie/app_audit', 'Java 17 / MyBatis-Plus',
     'mvn -q test', 'L0', 0, 0, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 60 DAY), UTC_TIMESTAMP(3));


-- ---------------------------------------------------------------------------
-- 2. requirement —— 三条需求，两条已拆解，一条还在草稿
-- ---------------------------------------------------------------------------
INSERT INTO requirement (id, project_id, title, raw_text, status, planned_at, deleted, created_at, updated_at)
VALUES
    (1, 1, '任务列表支持状态筛选与分页',
     '任务列表现在一次全量返回，超过二十条就难以翻阅。需要支持按状态筛选、按标题搜索，并分页展示。',
     'RUNNING', DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 120 MINUTE), 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 130 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 120 MINUTE)),
    (2, 1, '决策审批支持邮件链接一键批准',
     'L2/L3 决策挂起后要能通过邮件里的链接直接批准或驳回，链接一次有效、带过期时间。',
     'PLANNED', DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 100 MINUTE), 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 110 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 100 MINUTE)),
    (3, 2, '审计报表支持自定义时间范围',
     '报表目前固定按自然月统计，业务方要求能选任意起止日期，并支持导出。',
     'DRAFT', NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 90 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 90 MINUTE));


-- ---------------------------------------------------------------------------
-- 3. decision_rule —— 分级规则。全部是「只升不降」的升级规则，没有降级白名单
-- ---------------------------------------------------------------------------
INSERT INTO decision_rule (id, project_id, name, rule_type, pattern, target_level, priority, enabled, description, deleted, created_at, updated_at)
VALUES
    (1, NULL, '删除数据与表', 'KEYWORD', 'DROP |TRUNCATE |DELETE FROM|drop table', 'L3', 100, 1,
     '不可逆的数据破坏动作，永远强阻塞', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (2, NULL, '发布与打包目录', 'PATH_GLOB', 'deploy/**|scripts/release*|**/Dockerfile', 'L3', 95, 1,
     '改动发布链路影响面无法在本地验证', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (3, NULL, '库表结构变更', 'KEYWORD', 'ALTER TABLE|新增字段|删除字段|ADD COLUMN', 'L2', 90, 1,
     '涉及数据迁移，需要人工确认回滚方案', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (4, NULL, '迁移脚本改动', 'PATH_GLOB', '**/db/migration/**|**/*.sql', 'L2', 85, 1,
     'Flyway 脚本一旦执行过就不能改，写错只能追加新版本', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (5, NULL, '对外接口签名', 'KEYWORD', '接口签名|public API|Controller 入参|出参字段', 'L2', 80, 1,
     '签名变更会波及调用方，前端与第三方都要跟着改', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (6, 1, '不可逆操作（Regent）', 'REVERSIBILITY', '不可逆|irreversible', 'L3', 75, 1,
     '项目级覆盖：Regent 自己的数据不可逆操作一律强阻塞', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));


-- ---------------------------------------------------------------------------
-- 4. task —— 手写的九条「有故事」的任务，覆盖六种状态与三种来源
--
-- 时间线刻意分两段：这九条落在最近 18–42 分钟内，批量生成的落在 45 分钟以前。
-- 于是三个列表的首屏都是「有具体来由」的条目，而不是模板拼出来的第 39 号任务 ——
-- 演示数据的第一屏决定别人相不相信这套东西真的在跑。
--   创建 T、开始 T+1 分钟、结束 T+5 分钟
-- ---------------------------------------------------------------------------
INSERT INTO task (id, project_id, requirement_id, bug_id, source, title, prompt, status, priority,
                  codex_thread_id, branch_name, blocking_decision_id, attempt_count,
                  started_at, finished_at, deleted, created_at, updated_at)
VALUES
    -- 唯一一条执行中的任务：它是首屏第二行，时间线必须像「刚刚才跑起来」——
    -- 开始时间落在 41 分钟前，界面就会显示成一条跑了四十多分钟还没动静的任务。
    (1, 1, 1, NULL, 'REQUIREMENT', '修正登录页空指针',
     '登录页在未填手机号时提交会抛 NPE，定位并修复，补一条回归测试。', 'RUNNING', 10,
     'th_01HQ2K7M9XQ8R3', 'codex/task-1', NULL, 1,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 6 MINUTE), NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 7 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 MINUTE)),
    (2, 1, 1, NULL, 'REQUIREMENT', '给订单表增加 refund_no 字段',
     '订单表需要记录退款单号，新增 refund_no 列并补索引。', 'BLOCKED', 8,
     'th_01HQ2K8A1BB2C4', 'codex/task-2', NULL, 1,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 38 MINUTE), NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 39 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 37 MINUTE)),
    (3, 1, 1, NULL, 'MANUAL', '补充导出功能的单元测试',
     '导出功能目前零覆盖，补上正常路径与空结果两组用例。', 'DONE', 0,
     'th_01HQ2K5ZZ0QA19', 'codex/task-3', NULL, 1,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 35 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 30 MINUTE), 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 36 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 30 MINUTE)),
    (4, 1, NULL, 1, 'BUG_FIX', '修复并发下单重复扣库存',
     '压测下同一订单被扣两次库存，检查扣减的幂等键与事务边界。', 'FAILED', 20,
     'th_01HQ2K4PP8YZ07', 'codex/task-4', NULL, 2,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 32 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 28 MINUTE), 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 33 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 28 MINUTE)),
    (5, 1, 2, NULL, 'REQUIREMENT', '决策审批邮件模板支持中文',
     '审批邮件的模板目前是英文占位符，改成中文并带上决策链接。', 'BLOCKED', 6,
     'th_01HQ2K3MM7XX96', 'codex/task-5', NULL, 1,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 29 MINUTE), NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 30 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 28 MINUTE)),
    (6, 1, 2, NULL, 'MANUAL', '重构 codex JSONL 事件解析',
     '事件解析目前堆在一个方法里，按事件类型拆开并补测试。', 'DONE', 0,
     'th_01HQ2K2KK6WW85', 'codex/task-6', NULL, 1,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 26 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 22 MINUTE), 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 27 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 22 MINUTE)),
    (7, 2, 3, NULL, 'REQUIREMENT', '审计报表导出支持自定义时间范围',
     '报表导出增加起止日期参数，默认近三十天。', 'PENDING', 5,
     NULL, NULL, NULL, 0, NULL, NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 24 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 24 MINUTE)),
    (8, 2, NULL, 2, 'BUG_FIX', '修复报表分页错位',
     '翻到第二页时首行与第一页末行重复，检查 offset 计算。', 'CANCELLED', 0,
     'th_01HQ2K1II5VV74', 'codex/task-8', NULL, 1,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 20 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 16 MINUTE), 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 21 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 16 MINUTE)),
    (9, 1, 1, NULL, 'MANUAL', '给任务列表加状态统计卡片',
     '列表顶部展示各状态的任务数量，数据来自聚合查询。', 'PENDING', 3,
     NULL, NULL, NULL, 0, NULL, NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 18 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 18 MINUTE));


-- ---------------------------------------------------------------------------
-- 5. task —— 批量补足到 45 条，让分页（每页 20）真的有第二、第三页可翻
--    id 10–45，共 36 条；状态按 id 取模分布，priority 落在 0–4
--    创建时间落在 45–115 分钟前，全部早于第 4 段那九条，保证首屏是手写的那几条；
--    执行中(2)与阻塞中(3)是例外：它们的 started_at 故意贴近现在，见下面那两行的注释
-- ---------------------------------------------------------------------------
INSERT INTO task (id, project_id, requirement_id, bug_id, source, title, prompt, status, priority,
                  codex_thread_id, branch_name, blocking_decision_id, attempt_count,
                  started_at, finished_at, deleted, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 10
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 45
)
SELECT
    n,
    IF(MOD(n, 3) = 0, 2, 1),
    IF(MOD(n, 3) = 0, 3, 1),
    NULL,
    CASE WHEN MOD(n, 7) = 0 THEN 'BUG_FIX' WHEN MOD(n, 5) = 0 THEN 'MANUAL' ELSE 'REQUIREMENT' END,
    ELT(MOD(n, 12) + 1,
        '补齐任务详情页的空状态', '统一错误码到 DomainException', '给决策列表加等级筛选',
        '优化任务列表分页查询', '补充 Mapper 的集成测试', '修正时区转换导致的日期偏移',
        '给 webhook 通知加失败重试', '拆分过长的应用服务方法', '清理无用的日志输出',
        '为 Bug 单增加复现步骤字段', '完善部署脚本的回滚分支', '给任务状态机补边界测试'),
    CONCAT('按需求执行该任务，完成后运行项目验证命令并汇报结果。任务序号 ', n, '。'),
    CASE MOD(n, 9)
        WHEN 1 THEN 'PENDING'
        WHEN 2 THEN 'RUNNING'
        WHEN 3 THEN 'BLOCKED'
        WHEN 5 THEN 'FAILED'
        WHEN 6 THEN 'CANCELLED'
        ELSE 'DONE'
    END,
    MOD(n, 5),
    CASE MOD(n, 9) WHEN 1 THEN NULL WHEN 6 THEN NULL
         ELSE CONCAT('th_01HQ2K', LPAD(n, 2, '0'), 'ZZ') END,
    CASE MOD(n, 9) WHEN 1 THEN NULL WHEN 6 THEN NULL ELSE CONCAT('codex/task-', n) END,
    NULL,
    CASE MOD(n, 9) WHEN 1 THEN 0 WHEN 6 THEN 1 ELSE 1 END,
    -- 起始时间：待执行(1)没有；执行中(2)、阻塞中(3) 安排在这几分钟内 ——
    -- 让它们跟着批量时间线跑两小时，界面上就会显示成「跑了两小时还没停」，
    -- 那不是一条正在执行的任务该有的样子；其余状态落在 44 分钟以前这条批量时间线上。
    CASE MOD(n, 9)
        WHEN 1 THEN NULL
        WHEN 2 THEN DATE_SUB(UTC_TIMESTAMP(3), INTERVAL (3 + MOD(n, 7)) MINUTE)
        WHEN 3 THEN DATE_SUB(UTC_TIMESTAMP(3), INTERVAL (9 + MOD(n, 6)) MINUTE)
        ELSE DATE_SUB(UTC_TIMESTAMP(3), INTERVAL (44 + (45 - n) * 2) MINUTE)
    END,
    -- 结束时间只在「还没跑完」的状态下为空：待执行(1)、执行中(2)、阻塞中(3) 三种。
    -- 失败(5)、已终止(6) 都有结束时刻 —— 缺了它，界面上的「耗时」会按「到现在」算，
    -- 一条三天前就终止的任务会显示成跑了三天。
    CASE WHEN MOD(n, 9) IN (1, 2, 3) THEN NULL
         ELSE DATE_SUB(UTC_TIMESTAMP(3), INTERVAL (41 + (45 - n) * 2) MINUTE) END,
    0,
    DATE_SUB(UTC_TIMESTAMP(3), INTERVAL (45 + (45 - n) * 2) MINUTE),
    -- 更新时间 = 最后一次状态变化的时刻：排队中的等于创建时刻，
    -- 执行中的取一分钟前（等价于心跳），阻塞中的等于阻塞时刻，其余等于结束时刻。
    CASE MOD(n, 9)
        WHEN 1 THEN DATE_SUB(UTC_TIMESTAMP(3), INTERVAL (45 + (45 - n) * 2) MINUTE)
        WHEN 2 THEN DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 MINUTE)
        WHEN 3 THEN DATE_SUB(UTC_TIMESTAMP(3), INTERVAL (9 + MOD(n, 6)) MINUTE)
        ELSE DATE_SUB(UTC_TIMESTAMP(3), INTERVAL (41 + (45 - n) * 2) MINUTE)
    END
FROM seq;


-- ---------------------------------------------------------------------------
-- 6. decision —— 待批两条（对应两个阻塞任务）+ 已决四条（人工与 AI 各两条）
-- ---------------------------------------------------------------------------
INSERT INTO decision (id, task_id, project_id, ai_level, effective_level, matched_rule_id, status,
                      question, options, recommendation, reversible, blast_radius,
                      resolved_by, resolved_choice, resolution_note,
                      raised_at, resolved_at, created_at, updated_at)
VALUES
    (1, 2, 1, 'L1', 'L2', 3, 'PENDING',
     '给 orders 表新增 refund_no 字段并补索引，是否执行这次 ALTER TABLE？',
     '["新增字段并补索引", "先加字段，索引下个版本再加", "改为独立退款单表"]',
     '建议新增字段并补索引。当前表约 120 万行，ADD COLUMN 走 INSTANT 算法，锁表时间可忽略。',
     1, 'orders 表（生产约 120 万行）、退款链路读模型',
     NULL, NULL, NULL,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 37 MINUTE), NULL,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 37 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 37 MINUTE)),
    (2, 5, 1, 'L1', 'L3', 2, 'PENDING',
     '审批邮件模板改动落到了 deploy/ 目录下的脚本，需要确认是否继续。',
     '["继续，只改模板文本", "先把脚本改动拆到独立提交", "暂停，等发布窗口"]',
     '建议拆出脚本改动后再继续。模板文本与发布脚本混在一个提交里，回滚时会牵连发布流程。',
     0, 'deploy/release.sh（发布链路）、邮件模板',
     NULL, NULL, NULL,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 28 MINUTE), NULL,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 28 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 28 MINUTE)),
    (3, 3, 1, 'L1', 'L1', 5, 'APPROVED',
     '导出功能的单元测试需要新增一个测试依赖，是否允许？',
     '["允许新增依赖", "用现有依赖实现"]',
     '建议允许，mockito-inline 仅测试期使用，不进产物。',
     1, '仅 regent-core 的测试作用域',
     'HUMAN', '允许新增依赖', '测试期依赖，可以接受。',
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 36 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 34 MINUTE),
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 36 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 34 MINUTE)),
    (4, 6, 1, 'L0', 'L0', NULL, 'AUTO_RESOLVED',
     '事件解析的重构方式选择：按事件类型拆分为独立方法，或引入策略模式。',
     '["按类型拆方法", "引入策略模式"]',
     '按类型拆方法。当前只有六种事件类型，策略模式会多出六个类和一层间接。',
     1, '仅 execution 上下文内部',
     'AI', '按类型拆方法', 'L0 级改动，AI 自主决断并留痕。',
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 26 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 26 MINUTE),
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 26 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 26 MINUTE)),
    (5, 8, 2, 'L1', 'L2', 5, 'REJECTED',
     '报表分页错位的修复需要改动接口出参结构，是否执行？',
     '["改出参结构", "只修 offset 计算"]',
     '建议只修 offset。出参结构变更会让第三方调用方跟着改，收益不成比例。',
     1, '报表接口的第三方调用方',
     'HUMAN', '只修 offset 计算', '同意建议，改出参的事单独立项。',
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 20 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 17 MINUTE),
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 20 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 17 MINUTE)),
    (6, 1, 1, 'L0', 'L0', NULL, 'AUTO_RESOLVED',
     '登录页空指针的修复方式：在入口判空，还是在领域方法里抛业务异常？',
     '["入口判空", "领域方法抛异常"]',
     '两者都做。入口判空负责体验，领域方法抛异常负责兜底。',
     1, '仅登录链路',
     'AI', '两者都做', 'L0 级改动，AI 自主决断并留痕。',
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 5 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 5 MINUTE),
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 5 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 5 MINUTE));


-- ---------------------------------------------------------------------------
-- 7. 为批量生成的阻塞任务补决策单，并回填任务的 blocking_decision_id
--    阻塞中的任务必然对应一张待批决策单 —— 这是不变式，数据里也要成立
-- ---------------------------------------------------------------------------
INSERT INTO decision (id, task_id, project_id, ai_level, effective_level, matched_rule_id, status,
                      question, options, recommendation, reversible, blast_radius,
                      resolved_by, resolved_choice, resolution_note,
                      raised_at, resolved_at, created_at, updated_at)
SELECT
    100 + t.id,
    t.id,
    t.project_id,
    'L1',
    'L2',
    3,
    'PENDING',
    CONCAT('任务 #', t.id, '「', t.title, '」需要人工确认后才能继续，是否按建议执行？'),
    '["按建议执行", "换一种实现方式", "暂停该任务"]',
    'AI 自评为 L1，但改动触及库表结构，按规则升级为 L2，需人工批准。',
    1,
    CONCAT('项目 ', t.project_id, ' 的相关模块'),
    NULL, NULL, NULL,
    DATE_ADD(t.created_at, INTERVAL 1 MINUTE),
    NULL,
    DATE_ADD(t.created_at, INTERVAL 1 MINUTE),
    DATE_ADD(t.created_at, INTERVAL 1 MINUTE)
FROM task t
WHERE t.status = 'BLOCKED' AND t.id >= 10;

UPDATE task t
SET t.blocking_decision_id = (SELECT d.id FROM decision d WHERE d.task_id = t.id AND d.status = 'PENDING' ORDER BY d.id DESC LIMIT 1)
WHERE t.status = 'BLOCKED' AND t.id >= 1;


-- ---------------------------------------------------------------------------
-- 8. bug —— 缺陷单，覆盖三种严重级别与处理中的各种状态
-- ---------------------------------------------------------------------------
INSERT INTO bug (id, project_id, requirement_id, source_task_id, fix_task_id, source, title, severity, status,
                 repro, file_path, detail, wont_fix_reason, found_at, fixed_at, deleted, created_at, updated_at)
VALUES
    (1, 1, NULL, 4, 4, 'AI_DISCOVERED', '并发下单重复扣库存', 'MAJOR', 'FIXING',
     '两个线程同时提交同一订单，压测 200 并发下复现，库存被扣两次。',
     'src/main/java/order/StockService.java',
     '幂等键只在应用层校验，未落到数据库唯一约束，事务提交前有窗口。',
     NULL, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 27 MINUTE), NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 27 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 27 MINUTE)),
    (2, 2, NULL, 8, 8, 'AI_DISCOVERED', '报表分页错位', 'MINOR', 'CLOSED',
     '翻到第二页时首行与第一页末行重复。',
     'src/main/java/report/ReportQuery.java',
     'offset 用了 page * size，第一页算出来是 size 而不是 0。',
     NULL, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 26 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 23 MINUTE), 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 26 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 23 MINUTE)),
    (3, 1, 1, NULL, NULL, 'MANUAL', '导出 CSV 中文乱码', 'TRIVIAL', 'OPEN',
     '导出结果用 Excel 打开时中文变成问号，用文本编辑器打开正常。',
     'src/main/java/export/CsvWriter.java',
     '缺 UTF-8 BOM，Excel 按本地编码猜测。',
     NULL, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 25 MINUTE), NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 25 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 25 MINUTE)),
    (4, 1, NULL, NULL, NULL, 'MANUAL', '决策列表在 Safari 上等级徽章错位', 'TRIVIAL', 'OPEN',
     'Safari 17 下徽章与文字基线差 2px，Chrome 正常。',
     'regent-web/src/components/business/DecisionLevelBadge.vue',
     '行内元素的 vertical-align 继承被覆盖。',
     NULL, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 24 MINUTE), NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 24 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 24 MINUTE)),
    (5, 1, NULL, NULL, NULL, 'MANUAL', '任务列表筛选后页码未复位', 'MINOR', 'FIXED',
     '在第 3 页切换到「执行中」，列表变空但仍停在第 3 页。',
     'regent-web/src/composables/usePagination.ts',
     '筛选条件变化没有监听重置页码。',
     NULL, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 23 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 20 MINUTE), 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 23 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 20 MINUTE)),
    (6, 2, NULL, NULL, NULL, 'MANUAL', '审计日志时间戳时区偏移八小时', 'MAJOR', 'CLOSED',
     '导出文件里的时间比实际早八小时。',
     'src/main/java/audit/LogExporter.java',
     '写入时用了 LocalDateTime.now()，未按 UTC 转换。',
     NULL, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 22 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 19 MINUTE), 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 22 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 19 MINUTE)),
    (7, 1, NULL, NULL, NULL, 'MANUAL', '邮件通知重复发送', 'MINOR', 'OPEN',
     '同一决策在重试逻辑下收到两封邮件。',
     'src/main/java/notification/NotificationRouter.java',
     '重试未做去重，notification 表里的 attempt_count 没参与判定。',
     NULL, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 21 MINUTE), NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 21 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 21 MINUTE)),
    (8, 1, NULL, NULL, NULL, 'MANUAL', '长时间运行的任务内存持续增长', 'TRIVIAL', 'WONTFIX',
     '任务跑过两小时后堆占用缓慢上升。',
     'src/main/java/execution/TaskRunner.java',
     '事件流全量留在内存里。已确认在单任务结束后释放，暂不处理。',
     '已确认在单任务结束后释放，按当前规模不处理。',
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 20 MINUTE), NULL, 0,
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 20 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 20 MINUTE));


-- ---------------------------------------------------------------------------
-- 9. task_run —— 已结束任务的执行记录。token 数由 id 推出，保证每次重跑一致
-- ---------------------------------------------------------------------------
INSERT INTO task_run (id, task_id, attempt_no, status, exit_code, token_input, token_cached_input, token_output,
                      branch_name, summary, raw_result, schema_valid, started_at, finished_at, created_at)
SELECT
    t.id,
    t.id,
    1,
    -- 状态取值受 V1 的注释约束：SUCCESS/FAILED/BLOCKED/TIMEOUT。
    -- 阻塞来自「执行到一半需要人工确认」，是一次带结论的暂停，不是失败。
    CASE t.status WHEN 'DONE' THEN 'SUCCESS' WHEN 'BLOCKED' THEN 'BLOCKED' ELSE 'FAILED' END,
    CASE t.status WHEN 'DONE' THEN 0 WHEN 'BLOCKED' THEN NULL ELSE 1 END,
    1200 + t.id * 137,
    t.id * 41,
    300 + t.id * 29,
    t.branch_name,
    CASE t.status
        WHEN 'DONE' THEN CONCAT('完成：', t.title)
        WHEN 'BLOCKED' THEN CONCAT('暂停：', t.title, '（等待人工确认后恢复）')
        ELSE CONCAT('执行中断：', t.title, '（测试未通过或被人为终止）') END,
    NULL,
    CASE WHEN t.status IN ('DONE', 'BLOCKED') THEN 1 ELSE 0 END,
    t.started_at,
    -- 阻塞任务的这次尝试在提出决策时就结束了，结束时间必须落下来；
    -- 留空会让「耗时」一路算到现在，看起来像还在跑。
    CASE WHEN t.status = 'BLOCKED' THEN COALESCE(t.updated_at, DATE_ADD(t.started_at, INTERVAL 2 MINUTE))
         ELSE t.finished_at END,
    t.created_at
FROM task t
-- 执行中的任务不写 run 行：本表记的是「已结束的尝试」，进行中的尝试还没有结论，
-- 提前落一行会把 token 汇总和重试次数都算多。
WHERE t.status IN ('DONE', 'BLOCKED', 'FAILED', 'CANCELLED');


-- ---------------------------------------------------------------------------
-- 10. notification —— 待批决策的投递记录，一条成功一条失败（演示通道降级）
-- ---------------------------------------------------------------------------
INSERT INTO notification (id, biz_type, biz_id, channel, target, status, attempt_count, last_error, payload, sent_at, created_at)
VALUES
    (1, 'DECISION', 1, 'EMAIL', 'panyong@example.com', 'SENT', 1, NULL,
     '{"subject":"待批准：给订单表增加 refund_no 字段","level":"L2"}',
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 36 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 37 MINUTE)),
    (2, 'DECISION', 1, 'FEISHU', NULL, 'FAILED', 3, 'webhook 返回 429：触发限流',
     '{"subject":"待批准：给订单表增加 refund_no 字段","level":"L2"}',
     NULL, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 36 MINUTE)),
    (3, 'DECISION', 2, 'EMAIL', 'panyong@example.com', 'SENT', 1, NULL,
     '{"subject":"强阻塞：发布脚本改动需确认","level":"L3"}',
     DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 27 MINUTE), DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 28 MINUTE));


-- ---------------------------------------------------------------------------
-- 11. approval_token —— 待批决策的一次性令牌，只存 SHA-256，不存明文
-- ---------------------------------------------------------------------------
INSERT INTO approval_token (id, decision_id, token_hash, status, expires_at, used_at, used_ip, created_at)
VALUES
    (1, 1, SHA2(CONCAT('demo-token-1-', DATE_FORMAT(UTC_TIMESTAMP(3), '%Y%m%d')), 256), 'ACTIVE',
     DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 7 DAY), NULL, NULL, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 37 MINUTE)),
    (2, 2, SHA2(CONCAT('demo-token-2-', DATE_FORMAT(UTC_TIMESTAMP(3), '%Y%m%d')), 256), 'ACTIVE',
     DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 7 DAY), NULL, NULL, DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 28 MINUTE));
