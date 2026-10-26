import assert from 'node:assert/strict'
import test from 'node:test'

const {
  addWorkingDays,
  buildInitiativePlanInput,
  buildPlanStageSchedule,
  effortUnits,
  formatEffort,
  resolvePlanStageSeeds,
} = await import('../src/utils/initiativePlan.ts')

const stages = [
  {
    code: 'OUTLINE_DESIGN',
    name: '概要设计',
    description: '系统设计',
    roleCode: 'TECH_LEAD',
    defaultDurationDays: 3,
    durationDays: 2,
    headcount: 1,
  },
  {
    code: 'DEVELOPMENT',
    name: '研发',
    description: '编码实现',
    roleCode: 'DEVELOPER',
    defaultDurationDays: 10,
    durationDays: 1,
    headcount: 2,
  },
  {
    code: 'SMOKE',
    name: '冒烟测试',
    description: '可用性验证',
    roleCode: 'QA',
    defaultDurationDays: 1,
    durationDays: 3,
    headcount: 1,
  },
]

test('固定阶段按工作日顺序自动排期并跳过周末', () => {
  assert.equal(addWorkingDays('2026-10-30', 2), '2026-11-02')

  const schedule = buildPlanStageSchedule('2026-10-30', stages)
  assert.deepEqual(
    schedule.map(({ startDate, endDate, workloadDays }) => ({
      startDate,
      endDate,
      workloadDays,
    })),
    [
      { startDate: '2026-10-30', endDate: '2026-11-02', workloadDays: 2 },
      { startDate: '2026-11-03', endDate: '2026-11-03', workloadDays: 2 },
      { startDate: '2026-11-04', endDate: '2026-11-06', workloadDays: 3 },
    ],
  )
})

test('人天可换算为人周和人月', () => {
  const units = effortUnits(7)
  assert.equal(units.personDays, 7)
  assert.equal(units.personWeeks, 1.4)
  assert.equal(formatEffort(units.personMonths), '0.3')
})

test('固定阶段生成可直接提交后端的版本计划', () => {
  const input = buildInitiativePlanInput('2026-10-30', '联调窗口待确认', stages)

  assert.equal(input.planStartDate, '2026-10-30')
  assert.equal(input.planEndDate, '2026-11-06')
  assert.equal(input.milestones, '联调窗口待确认')
  assert.deepEqual(input.items, [
    {
      name: '概要设计',
      roleCode: 'TECH_LEAD',
      startDate: '2026-10-30',
      endDate: '2026-11-02',
      headcount: 1,
    },
    {
      name: '研发',
      roleCode: 'DEVELOPER',
      startDate: '2026-11-03',
      endDate: '2026-11-03',
      headcount: 2,
    },
    {
      name: '冒烟测试',
      roleCode: 'QA',
      startDate: '2026-11-04',
      endDate: '2026-11-06',
      headcount: 1,
    },
  ])
})

test('流程模板阶段使用对应责任角色与默认工期', () => {
  const seeds = resolvePlanStageSeeds([
    {
      code: 'AUTOMATION',
      name: '自动化回归',
      order: 3,
      scope: 'REQUIREMENT',
      approverRole: 'QA',
      description: '自动化执行',
    },
    {
      code: 'TRANSFER',
      name: '转测',
      order: 4,
      scope: 'REQUIREMENT',
      approverRole: 'QA',
      description: '转测准入',
    },
    {
      code: 'PRODUCTION_RELEASE',
      name: '生产发布',
      order: 5,
      scope: 'VERSION',
      approverRole: 'RELEASE_MANAGER',
      description: '生产部署',
    },
    {
      code: 'OBSERVE',
      name: '发布观察',
      order: 6,
      scope: 'VERSION',
      approverRole: 'OWNER',
      description: '线上观察',
    },
    {
      code: 'INTEGRATION',
      name: '集成测试',
      order: 7,
      scope: 'VERSION',
      approverRole: 'QA',
      description: '集成验证',
    },
    {
      code: 'DEVELOPMENT',
      name: '研发',
      order: 2,
      scope: 'REQUIREMENT',
      approverRole: 'DEVELOPER',
      description: '编码',
    },
    {
      code: 'OUTLINE_DESIGN',
      name: '概要设计',
      order: 1,
      scope: 'REQUIREMENT',
      approverRole: 'TECH_LEAD',
      description: '设计',
    },
  ])

  assert.deepEqual(
    seeds.map(({ code }) => code),
    ['OUTLINE_DESIGN', 'DEVELOPMENT', 'INTEGRATION', 'SPECIAL_TEST'],
  )
  assert.equal(seeds[0].roleCode, 'TECH_LEAD')
  assert.equal(seeds[1].defaultDurationDays, 10)
  assert.equal(seeds[3].roleCode, 'TESTER')
})

test('没有流程模板快照时使用内置标准阶段', () => {
  const seeds = resolvePlanStageSeeds([])
  assert.equal(seeds.length, 12)
  assert.equal(seeds[0].code, 'VERSION_INITIATION')
  assert.equal(seeds.at(-1).code, 'CLOSE')
  assert.equal(seeds.some(({ code }) => code === 'AUTOMATION'), false)
  assert.equal(seeds.some(({ code }) => code === 'TRANSFER'), false)
  assert.equal(seeds.some(({ code }) => code === 'PRODUCTION_RELEASE'), false)
  assert.equal(seeds.some(({ code }) => code === 'OBSERVE'), false)
  assert.equal(
    seeds.findIndex(({ code }) => code === 'SPECIAL_TEST'),
    seeds.findIndex(({ code }) => code === 'INTEGRATION') + 1,
  )
  assert.equal(seeds.find(({ code }) => code === 'SPECIAL_TEST')?.roleCode, 'TESTER')
})
