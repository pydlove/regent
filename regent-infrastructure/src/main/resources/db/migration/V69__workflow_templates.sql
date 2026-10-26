-- 全局研发流程模板。模板发布后不可修改；项目选择模板，产品版本固定模板快照。
CREATE TABLE `workflow_template` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `template_code` VARCHAR(64)  NOT NULL,
    `version_no`    INT          NOT NULL,
    `name`          VARCHAR(160) NOT NULL,
    `description`   TEXT         DEFAULT NULL,
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED',
    `created_at`    DATETIME(3)  NOT NULL,
    `updated_at`    DATETIME(3)  NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workflow_template_code_version` (`template_code`, `version_no`),
    KEY `idx_workflow_template_status` (`status`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '全局研发流程模板版本';

CREATE TABLE `workflow_template_stage` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `template_id`   BIGINT       NOT NULL,
    `stage_code`    VARCHAR(48)  NOT NULL,
    `stage_name`    VARCHAR(80)  NOT NULL,
    `stage_order`   INT          NOT NULL,
    `scope_code`    VARCHAR(24)  NOT NULL,
    `approver_role` VARCHAR(32)  DEFAULT NULL,
    `description`   VARCHAR(500) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_workflow_template_stage_code` (`template_id`, `stage_code`),
    KEY `idx_workflow_template_stage_order` (`template_id`, `stage_order`),
    CONSTRAINT `fk_workflow_template_stage_template`
        FOREIGN KEY (`template_id`) REFERENCES `workflow_template` (`id`)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '研发流程模板阶段与门禁配置';

CREATE TABLE `project_workflow_binding` (
    `project_id`  BIGINT      NOT NULL,
    `template_id` BIGINT      NOT NULL,
    `selected_at` DATETIME(3) NOT NULL,
    PRIMARY KEY (`project_id`),
    KEY `idx_project_workflow_template` (`template_id`),
    CONSTRAINT `fk_project_workflow_project`
        FOREIGN KEY (`project_id`) REFERENCES `project` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_project_workflow_template`
        FOREIGN KEY (`template_id`) REFERENCES `workflow_template` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '项目选择的默认研发流程模板';

CREATE TABLE `product_version_workflow_binding` (
    `product_version_id` BIGINT      NOT NULL,
    `template_id`        BIGINT      NOT NULL,
    `snapped_at`         DATETIME(3) NOT NULL,
    PRIMARY KEY (`product_version_id`),
    KEY `idx_product_version_workflow_template` (`template_id`),
    CONSTRAINT `fk_product_version_workflow_version`
        FOREIGN KEY (`product_version_id`) REFERENCES `product_version` (`id`)
        ON DELETE CASCADE,
    CONSTRAINT `fk_product_version_workflow_template`
        FOREIGN KEY (`template_id`) REFERENCES `workflow_template` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '产品版本固定使用的研发流程模板快照';

INSERT INTO `workflow_template`
    (`id`, `template_code`, `version_no`, `name`, `description`, `status`, `created_at`, `updated_at`)
VALUES
    (1, 'standard_waterfall', 1, '标准瀑布式',
     '按版本立项、需求评审、详细设计、设计评审、研发、测试、转测、集成、回归和发布依次推进的阶段门禁流程。',
     'PUBLISHED', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO `workflow_template_stage`
    (`template_id`, `stage_code`, `stage_name`, `stage_order`, `scope_code`, `approver_role`, `description`)
VALUES
    (1, 'VERSION_INITIATION', '版本立项', 1, 'VERSION', 'OWNER', '产品负责人提交立项材料，项目负责人审批版本目标和资源投入'),
    (1, 'REQUIREMENT_REVIEW', '需求评审', 2, 'REQUIREMENT', 'OWNER', '产品负责人提交线下评审材料，项目负责人审核需求范围和验收标准'),
    (1, 'DESIGN', '详细设计', 3, 'REQUIREMENT', 'TECH_LEAD', '技术负责人输出设计规格、接口、数据和测试策略'),
    (1, 'DESIGN_REVIEW', '设计评审', 4, 'REQUIREMENT', 'TECH_LEAD', '技术负责人组织设计评审并确认技术风险'),
    (1, 'DEVELOPMENT', '研发', 5, 'REQUIREMENT', 'DEVELOPER', '开发完成任务实现、代码审阅和单元测试'),
    (1, 'SMOKE', '冒烟测试', 6, 'REQUIREMENT', 'QA', '测试负责人确认构建制品基本可用'),
    (1, 'AUTOMATION', '自动化回归', 7, 'REQUIREMENT', 'QA', '执行自动化回归并保存测试报告'),
    (1, 'TRANSFER', '转测', 8, 'REQUIREMENT', 'QA', '测试负责人确认测试结果、遗留风险和转测条件'),
    (1, 'INTEGRATION', '集成测试', 9, 'VERSION', 'QA', '在集成环境验证跨模块和接口协同'),
    (1, 'REGRESSION', '版本回归', 10, 'VERSION', 'QA', '执行版本级回归测试并输出报告'),
    (1, 'RELEASE_REVIEW', '发版评审', 11, 'VERSION', 'RELEASE_MANAGER', '发布负责人确认发版范围、风险和回滚方案'),
    (1, 'PRODUCTION_RELEASE', '生产发布', 12, 'VERSION', 'RELEASE_MANAGER', '发布同一制品到生产并完成发布冒烟'),
    (1, 'OBSERVE', '发布观察', 13, 'VERSION', 'OWNER', '观察线上指标和用户反馈'),
    (1, 'CLOSE', '关闭', 14, 'VERSION', 'OWNER', '确认复盘、遗留问题和版本关闭');

INSERT INTO `project_workflow_binding` (`project_id`, `template_id`, `selected_at`)
SELECT `id`, 1, UTC_TIMESTAMP(3)
FROM `project`
WHERE `deleted` = 0;

INSERT INTO `product_version_workflow_binding` (`product_version_id`, `template_id`, `snapped_at`)
SELECT `id`, 1, UTC_TIMESTAMP(3)
FROM `product_version`;
