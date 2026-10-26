-- 标准瀑布模板 V2：在设计前增加版本计划检查与概要设计阶段。
INSERT INTO `workflow_template`
    (`template_code`, `version_no`, `name`, `description`, `status`, `created_at`, `updated_at`)
VALUES
    ('standard_waterfall', 2, '标准瀑布式',
     '版本立项包含计划与工作量估算；需求评审后先完成概要设计，再按模块分配研发并提交详细设计，设计评审通过后进入研发。',
     'PUBLISHED', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

SET @standard_waterfall_v2 = LAST_INSERT_ID();
SET @standard_waterfall_v1 = (
    SELECT `id` FROM `workflow_template`
    WHERE `template_code` = 'standard_waterfall' AND `version_no` = 1
);

INSERT INTO `workflow_template_stage`
    (`template_id`, `stage_code`, `stage_name`, `stage_order`, `scope_code`, `approver_role`, `description`)
VALUES
    (@standard_waterfall_v2, 'VERSION_INITIATION', '版本立项', 1, 'VERSION', 'OWNER', '产品负责人提交目标与评审材料，项目负责人补充版本计划、排期和工作量估算后审批'),
    (@standard_waterfall_v2, 'REQUIREMENT_REVIEW', '需求评审', 2, 'REQUIREMENT', 'OWNER', '产品负责人提交线下评审材料，项目负责人审核需求范围和验收标准'),
    (@standard_waterfall_v2, 'OUTLINE_DESIGN', '概要设计', 3, 'REQUIREMENT', 'TECH_LEAD', '技术负责人提交概要设计摘要并上传设计文档'),
    (@standard_waterfall_v2, 'DESIGN', '详细设计', 4, 'REQUIREMENT', 'TECH_LEAD', '技术负责人拆分模块并分配研发，各研发提交自己模块的详细设计'),
    (@standard_waterfall_v2, 'DESIGN_REVIEW', '设计评审', 5, 'REQUIREMENT', 'TECH_LEAD', '技术负责人汇总模块设计并组织评审'),
    (@standard_waterfall_v2, 'DEVELOPMENT', '研发', 6, 'REQUIREMENT', 'DEVELOPER', '开发按模块设计实现、自测并提交'),
    (@standard_waterfall_v2, 'SMOKE', '冒烟测试', 7, 'REQUIREMENT', 'QA', '测试负责人确认构建制品基本可用'),
    (@standard_waterfall_v2, 'AUTOMATION', '自动化回归', 8, 'REQUIREMENT', 'QA', '执行自动化回归并保存测试报告'),
    (@standard_waterfall_v2, 'TRANSFER', '转测', 9, 'REQUIREMENT', 'QA', '测试负责人确认测试结果、遗留风险和转测条件'),
    (@standard_waterfall_v2, 'INTEGRATION', '集成测试', 10, 'VERSION', 'QA', '在集成环境验证跨模块和接口协同'),
    (@standard_waterfall_v2, 'REGRESSION', '版本回归', 11, 'VERSION', 'QA', '执行版本级回归测试并输出报告'),
    (@standard_waterfall_v2, 'RELEASE_REVIEW', '发版评审', 12, 'VERSION', 'RELEASE_MANAGER', '发布负责人确认发版范围、风险和回滚方案'),
    (@standard_waterfall_v2, 'PRODUCTION_RELEASE', '生产发布', 13, 'VERSION', 'RELEASE_MANAGER', '发布同一制品到生产并完成发布冒烟'),
    (@standard_waterfall_v2, 'OBSERVE', '发布观察', 14, 'VERSION', 'OWNER', '观察线上指标和用户反馈'),
    (@standard_waterfall_v2, 'CLOSE', '关闭', 15, 'VERSION', 'OWNER', '确认复盘、遗留问题和版本关闭');

UPDATE `workflow_template`
SET `status` = 'SUPERSEDED', `updated_at` = UTC_TIMESTAMP(3)
WHERE `id` = @standard_waterfall_v1;

UPDATE `project_workflow_binding`
SET `template_id` = @standard_waterfall_v2, `selected_at` = UTC_TIMESTAMP(3)
WHERE `template_id` = @standard_waterfall_v1;

UPDATE `product_version_workflow_binding`
SET `template_id` = @standard_waterfall_v2, `snapped_at` = UTC_TIMESTAMP(3)
WHERE `template_id` = @standard_waterfall_v1;
