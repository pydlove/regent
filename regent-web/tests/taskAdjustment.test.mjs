import assert from 'node:assert/strict'
import test from 'node:test'

const { filterAdjustmentSourceTasks } = await import('../src/utils/taskAdjustment.ts')

const tasks = [
  { id: 1, title: '登录页调整' },
  { id: 2, title: '移动端布局' },
  { id: 12, title: '发布中心优化' },
]

test('调整来源排除目标任务并按编号或标题搜索', () => {
  assert.deepEqual(
    filterAdjustmentSourceTasks(tasks, 1, ''),
    [
      { id: 2, title: '移动端布局' },
      { id: 12, title: '发布中心优化' },
    ],
  )
  assert.deepEqual(filterAdjustmentSourceTasks(tasks, 1, '布局'), [
    { id: 2, title: '移动端布局' },
  ])
  assert.deepEqual(filterAdjustmentSourceTasks(tasks, 1, '12'), [
    { id: 12, title: '发布中心优化' },
  ])
})

test('调整来源搜索忽略首尾空格和英文大小写', () => {
  assert.deepEqual(
    filterAdjustmentSourceTasks(
      [{ id: 3, title: 'Fix Login Redirect' }],
      1,
      '  login  ',
    ),
    [{ id: 3, title: 'Fix Login Redirect' }],
  )
})
