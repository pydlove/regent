import assert from 'node:assert/strict'
import test from 'node:test'

const { canCreateContinuation } = await import('../src/types/task.ts')

test('执行中、待批准和已收场任务允许继续创建', () => {
  for (const status of ['RUNNING', 'BLOCKED', 'DONE', 'FAILED', 'CANCELLED']) {
    assert.equal(canCreateContinuation(status), true, status)
  }
})

test('尚未进入执行链路或仍在排队的任务不能继续创建', () => {
  for (const status of ['PENDING', 'QUEUED']) {
    assert.equal(canCreateContinuation(status), false, status)
  }
})
