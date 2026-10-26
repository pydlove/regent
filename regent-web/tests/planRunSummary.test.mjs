import assert from 'node:assert/strict'
import test from 'node:test'

import {
  orderPlanRunItems,
  planRunHeadline,
} from '../src/utils/planRunSummary.ts'

const run = {
  id: 1,
  planId: 1,
  planName: '计划',
  trigger: 'MANUAL',
  status: 'PARTIAL',
  taskCount: 5,
  progressCount: 5,
  succeededCount: 3,
  failedCount: 1,
  cancelledCount: 1,
  skippedCount: 0,
  runningCount: 0,
  blockedCount: 0,
  summary: null,
  startedAt: '2026-10-21T08:00:00Z',
  finishedAt: '2026-10-21T08:10:00Z',
}

test('任务结果先展示失败和阻塞，再展示成功', () => {
  const items = [
    { taskId: 4, title: '成功', status: 'SUCCEEDED', detail: null, updatedAt: '' },
    { taskId: 3, title: '运行', status: 'RUNNING', detail: null, updatedAt: '' },
    { taskId: 2, title: '失败', status: 'FAILED', detail: null, updatedAt: '' },
    { taskId: 1, title: '阻塞', status: 'BLOCKED', detail: null, updatedAt: '' },
  ]

  assert.deepEqual(orderPlanRunItems(items).map((item) => item.taskId), [2, 1, 3, 4])
})

test('执行总结用结果数据生成可扫读的一句话', () => {
  assert.equal(planRunHeadline(run), '3 个成功，2 个未成功')
  assert.equal(
    planRunHeadline({ ...run, status: 'RUNNING', progressCount: 2, taskCount: 5 }),
    '2 / 5 个任务已收场',
  )
  assert.equal(
    planRunHeadline({ ...run, status: 'COMPLETED', succeededCount: 5 }),
    '5 个任务全部成功',
  )
})
