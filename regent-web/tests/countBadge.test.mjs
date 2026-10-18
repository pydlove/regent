import assert from 'node:assert/strict'
import test from 'node:test'

import { formatCountBadge } from '../src/utils/countBadge.ts'

test('小数字保留精确值，超过上限后收敛为 999+', () => {
  assert.equal(formatCountBadge(0), '')
  assert.equal(formatCountBadge(82), '82')
  assert.equal(formatCountBadge(999), '999')
  assert.equal(formatCountBadge(1000), '999+')
  assert.equal(formatCountBadge(12_345), '999+')
})

test('非法或负数不渲染，自定义上限同样生效', () => {
  assert.equal(formatCountBadge(-1), '')
  assert.equal(formatCountBadge(Number.NaN), '')
  assert.equal(formatCountBadge(Number.POSITIVE_INFINITY), '')
  assert.equal(formatCountBadge(100, 99), '99+')
  assert.equal(formatCountBadge(82.9), '82')
})
