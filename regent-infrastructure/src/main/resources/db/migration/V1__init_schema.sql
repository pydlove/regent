-- =====================================================================================
-- Regent 初始表结构
--
-- 约定（详见 TECH_STACK.md 3.6 与 AGENTS.md「数据库与 Flyway」）：
--   * 字符集统一 utf8mb4 / utf8mb4_0900_ai_ci
--   * 主键 BIGINT AUTO_INCREMENT，由数据库生成，仓储插入后回填
--   * 状态字段用 VARCHAR 存枚举名，不用 MySQL ENUM（改枚举不必动表结构）
--   * 时间统一 DATETIME(3) 且存 UTC，由应用显式写入，**不使用数据库默认值与 ON UPDATE**
--     （避免连接会话时区不同导致同一行在不同客户端下时间戳不一致）
--   * 逻辑删除用 deleted TINYINT(1)，不用硬删除，保证决策与执行的审计链完整
--
-- 外键策略：本文件**使用**外键约束。理由是这些表基本是追加型（运行记录、事件流、决策、
--   投递记录），孤儿行没有意义，外键能挡住真实的写入 bug。唯一的例外见 notification
--   （多态引用无法建外键）与 task.blocking_decision_id（与 decision.task_id 互相引用，
--   建外键会形成环，故只建索引）。
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- 1. project —— 项目注册表
-- -------------------------------------------------------------------------------------
CREATE TABLE `project` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT,
    `code`                    VARCHAR(64)  NOT NULL COMMENT '项目唯一标识，如 app_audit',
    `name`                    VARCHAR(128) NOT NULL COMMENT '展示名称',
    `path`                    VARCHAR(512) NOT NULL COMMENT '工作区绝对路径，codex exec -C 用',
    `tech_stack`              VARCHAR(128) DEFAULT NULL COMMENT '如 Java 17 / Maven',
    `verify_method`           VARCHAR(512) DEFAULT NULL COMMENT '验证方式，如 mvn -q test；为空则只能做静态检查',
    `default_decision_level`  VARCHAR(8)   NOT NULL DEFAULT 'L1' COMMENT '项目级默认自动等级，高于此等级一律升级人工',
    `enabled`                 TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '暂停开关，0 表示不再派发新任务',
    `deleted`                 TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at`              DATETIME(3)  NOT NULL,
    `updated_at`              DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_code` (`code`),
    KEY `idx_project_enabled` (`enabled`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '项目注册表';


-- -------------------------------------------------------------------------------------
-- 2. requirement —— 需求
-- -------------------------------------------------------------------------------------
CREATE TABLE `requirement` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`  BIGINT       NOT NULL,
    `title`       VARCHAR(256) NOT NULL,
    `raw_text`    MEDIUMTEXT   NOT NULL COMMENT '需求原文，保留未加工描述',
    `status`      VARCHAR(16)  NOT NULL COMMENT 'DRAFT/PLANNED/RUNNING/DONE/CANCELLED',
    `planned_at`  DATETIME(3)  DEFAULT NULL COMMENT '拆解为任务的时刻',
    `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at`  DATETIME(3)  NOT NULL,
    `updated_at`  DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_requirement_project` (`project_id`, `status`),
    CONSTRAINT `fk_requirement_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '需求';


-- -------------------------------------------------------------------------------------
-- 3. task —— 执行单元（聚合根）
-- -------------------------------------------------------------------------------------
CREATE TABLE `task` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`           BIGINT       NOT NULL,
    `requirement_id`       BIGINT       DEFAULT NULL COMMENT '缺陷派生任务可无需求',
    `bug_id`               BIGINT       DEFAULT NULL COMMENT '来源缺陷单',
    `source`               VARCHAR(16)  NOT NULL COMMENT 'REQUIREMENT/BUG_FIX/MANUAL',
    `title`                VARCHAR(256) NOT NULL,
    `prompt`               MEDIUMTEXT   NOT NULL COMMENT '交给 codex exec 的完整指令',
    `status`               VARCHAR(16)  NOT NULL COMMENT 'PENDING/RUNNING/BLOCKED/DONE/FAILED/CANCELLED',
    `priority`             INT          NOT NULL DEFAULT 0 COMMENT '数值越大越先派发',
    `codex_thread_id`      VARCHAR(64)  DEFAULT NULL COMMENT '★ 断点续跑的钥匙，取自 codex exec --json 的 thread_id',
    `branch_name`          VARCHAR(128) DEFAULT NULL COMMENT '独立分支，绝不直接改主干',
    `blocking_decision_id` BIGINT       DEFAULT NULL COMMENT '当前阻塞它的决策；不建外键，避免与 decision.task_id 成环',
    `attempt_count`        INT          NOT NULL DEFAULT 0,
    `started_at`           DATETIME(3)  DEFAULT NULL,
    `finished_at`          DATETIME(3)  DEFAULT NULL,
    `deleted`              TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at`           DATETIME(3)  NOT NULL,
    `updated_at`           DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_task_dispatch` (`status`, `priority`, `created_at`) COMMENT '派发扫描用',
    KEY `idx_task_project` (`project_id`, `status`),
    KEY `idx_task_requirement` (`requirement_id`),
    KEY `idx_task_blocking_decision` (`blocking_decision_id`),
    CONSTRAINT `fk_task_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_task_requirement` FOREIGN KEY (`requirement_id`) REFERENCES `requirement` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务';


-- -------------------------------------------------------------------------------------
-- 4. task_run —— 每次执行的记录
-- -------------------------------------------------------------------------------------
CREATE TABLE `task_run` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`            BIGINT       NOT NULL,
    `attempt_no`         INT          NOT NULL COMMENT '第几次尝试，从 1 开始',
    `status`             VARCHAR(16)  NOT NULL COMMENT 'SUCCESS/FAILED/BLOCKED/TIMEOUT',
    `exit_code`          INT          DEFAULT NULL COMMENT 'codex 进程退出码',
    `token_input`        BIGINT       NOT NULL DEFAULT 0,
    `token_cached_input` BIGINT       NOT NULL DEFAULT 0,
    `token_output`       BIGINT       NOT NULL DEFAULT 0,
    `branch_name`        VARCHAR(128) DEFAULT NULL,
    `summary`            TEXT         DEFAULT NULL COMMENT '结构化出参里的 summary',
    `raw_result`         MEDIUMTEXT   DEFAULT NULL COMMENT 'codex 末条消息原文，用于复盘与排错',
    `schema_valid`       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '出参是否通过 JSON Schema 校验；0 表示被当作失败处理',
    `started_at`         DATETIME(3)  NOT NULL,
    `finished_at`        DATETIME(3)  DEFAULT NULL,
    `created_at`         DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_run_task_attempt` (`task_id`, `attempt_no`),
    CONSTRAINT `fk_run_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '任务执行记录';


-- -------------------------------------------------------------------------------------
-- 5. run_event —— codex exec --json 的事件流
-- -------------------------------------------------------------------------------------
CREATE TABLE `run_event` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `task_run_id` BIGINT      NOT NULL,
    `seq`         INT         NOT NULL COMMENT '事件在本次运行内的序号，从 0 开始',
    `event_type`  VARCHAR(64) NOT NULL COMMENT 'thread.started / item.completed / turn.completed ...',
    `payload`     JSON        DEFAULT NULL COMMENT '事件原始内容',
    `occurred_at` DATETIME(3) DEFAULT NULL,
    `created_at`  DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_run_seq` (`task_run_id`, `seq`),
    CONSTRAINT `fk_event_run` FOREIGN KEY (`task_run_id`) REFERENCES `task_run` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'codex 执行事件流';
-- 事件流是行数增长最快的一张表，后续需按 task_run 归档或定期清理，见 TECH_STACK.md 风险项。


-- -------------------------------------------------------------------------------------
-- 6. decision —— 决策单（核心域审计记录）
-- -------------------------------------------------------------------------------------
CREATE TABLE `decision` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `task_id`         BIGINT       NOT NULL,
    `project_id`      BIGINT       NOT NULL,
    `ai_level`        VARCHAR(8)   NOT NULL COMMENT 'AI 自评等级，原样保留以供审计',
    `effective_level` VARCHAR(8)   NOT NULL COMMENT '仲裁后等级（就高不就低）。二者不同即说明规则起了作用',
    `matched_rule_id` BIGINT       DEFAULT NULL COMMENT '命中的分级规则；为空表示仅凭 AI 自评',
    `status`          VARCHAR(16)  NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/AUTO_RESOLVED',
    `question`        TEXT         NOT NULL COMMENT '需要拍板的问题',
    `options`         JSON         DEFAULT NULL COMMENT '候选方案',
    `recommendation`  TEXT         DEFAULT NULL COMMENT 'AI 的建议及理由',
    `reversible`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否可逆，是自动决策的前提',
    `blast_radius`    VARCHAR(512) DEFAULT NULL COMMENT '影响范围描述',
    `resolved_by`     VARCHAR(16)  DEFAULT NULL COMMENT '★ AI / HUMAN，谁做的决定',
    `resolved_choice` VARCHAR(64)  DEFAULT NULL,
    `resolution_note` TEXT         DEFAULT NULL,
    `raised_at`       DATETIME(3)  NOT NULL,
    `resolved_at`     DATETIME(3)  DEFAULT NULL,
    `created_at`      DATETIME(3)  NOT NULL,
    `updated_at`      DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_decision_pending` (`status`, `effective_level`, `raised_at`) COMMENT '待批扫描用',
    KEY `idx_decision_task` (`task_id`),
    CONSTRAINT `fk_decision_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
    CONSTRAINT `fk_decision_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '决策单';


-- -------------------------------------------------------------------------------------
-- 7. decision_rule —— 决策分级规则
-- -------------------------------------------------------------------------------------
CREATE TABLE `decision_rule` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`   BIGINT       DEFAULT NULL COMMENT 'NULL 表示全局规则',
    `name`         VARCHAR(128) NOT NULL,
    `rule_type`    VARCHAR(32)  NOT NULL COMMENT 'PATH_GLOB/KEYWORD/CHANGED_FILES/REVERSIBILITY',
    `pattern`      VARCHAR(512) NOT NULL COMMENT '通配或关键词',
    `target_level` VARCHAR(8)   NOT NULL COMMENT '命中后采用的等级',
    `priority`     INT          NOT NULL DEFAULT 0 COMMENT '数值越大越先匹配',
    `enabled`      TINYINT(1)   NOT NULL DEFAULT 1,
    `description`  VARCHAR(512) DEFAULT NULL,
    `deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at`   DATETIME(3)  NOT NULL,
    `updated_at`   DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_rule_match` (`enabled`, `deleted`, `project_id`, `priority`),
    CONSTRAINT `fk_rule_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '决策分级规则';


-- -------------------------------------------------------------------------------------
-- 8. bug —— 缺陷单
-- -------------------------------------------------------------------------------------
CREATE TABLE `bug` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `project_id`      BIGINT       NOT NULL,
    `requirement_id`  BIGINT       DEFAULT NULL,
    `source_task_id`  BIGINT       DEFAULT NULL COMMENT '发现它的任务',
    `fix_task_id`     BIGINT       DEFAULT NULL COMMENT '派生的修复任务；不建外键避免与 task.bug_id 成环',
    `title`           VARCHAR(256) NOT NULL,
    `severity`        VARCHAR(16)  NOT NULL COMMENT 'HIGH/MEDIUM/LOW',
    `status`          VARCHAR(16)  NOT NULL COMMENT 'OPEN/FIXING/FIXED/VERIFIED/CLOSED/WONT_FIX',
    `repro`           TEXT         DEFAULT NULL COMMENT '复现步骤',
    `file_path`       VARCHAR(512) DEFAULT NULL COMMENT '定位到的文件',
    `detail`          TEXT         DEFAULT NULL,
    `found_at`        DATETIME(3)  NOT NULL,
    `fixed_at`        DATETIME(3)  DEFAULT NULL,
    `deleted`         TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at`      DATETIME(3)  NOT NULL,
    `updated_at`      DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_bug_project_status` (`project_id`, `status`, `severity`),
    KEY `idx_bug_fix_task` (`fix_task_id`),
    CONSTRAINT `fk_bug_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
    CONSTRAINT `fk_bug_source_task` FOREIGN KEY (`source_task_id`) REFERENCES `task` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '缺陷单';


-- -------------------------------------------------------------------------------------
-- 9. notification —— 通知投递记录
-- -------------------------------------------------------------------------------------
CREATE TABLE `notification` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT,
    `biz_type`      VARCHAR(32)   NOT NULL COMMENT 'DECISION/BUG/DAILY_DIGEST',
    `biz_id`        BIGINT        DEFAULT NULL COMMENT '多态引用，无法建外键',
    `channel`       VARCHAR(32)   NOT NULL COMMENT 'EMAIL/FEISHU/SERVERCHAN/PUSHPLUS/DINGTALK',
    `target`        VARCHAR(256)  DEFAULT NULL COMMENT '收件地址或 webhook 标识',
    `status`        VARCHAR(16)   NOT NULL COMMENT 'PENDING/SENT/FAILED',
    `attempt_count` INT           NOT NULL DEFAULT 0,
    `last_error`    VARCHAR(1024) DEFAULT NULL,
    `payload`       JSON          DEFAULT NULL,
    `sent_at`       DATETIME(3)   DEFAULT NULL,
    `created_at`    DATETIME(3)   NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_notify_biz` (`biz_type`, `biz_id`),
    KEY `idx_notify_retry` (`status`, `attempt_count`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '通知投递记录';


-- -------------------------------------------------------------------------------------
-- 10. approval_token —— 一次性审批令牌
-- -------------------------------------------------------------------------------------
CREATE TABLE `approval_token` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `decision_id` BIGINT      NOT NULL,
    `token_hash`  CHAR(64)    NOT NULL COMMENT '★ 只存 SHA-256，不存明文。邮件里的链接被转发或库被读走都无法直接用于审批',
    `status`      VARCHAR(16) NOT NULL COMMENT 'ACTIVE/USED/EXPIRED/REVOKED',
    `expires_at`  DATETIME(3) NOT NULL,
    `used_at`     DATETIME(3) DEFAULT NULL,
    `used_ip`     VARCHAR(64) DEFAULT NULL,
    `created_at`  DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token_hash` (`token_hash`),
    KEY `idx_token_decision` (`decision_id`, `status`),
    CONSTRAINT `fk_token_decision` FOREIGN KEY (`decision_id`) REFERENCES `decision` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '一次性审批令牌';
