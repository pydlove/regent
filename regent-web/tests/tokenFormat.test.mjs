import assert from 'node:assert/strict'
import test from 'node:test'

const { formatTokenYi, formatTokens } = await import('../src/utils/format.ts')

test('Token 超过一亿时显示对应的亿单位', () => {
  assert.equal(formatTokenYi(1_234_400_000), '12.34')
  assert.equal(formatTokenYi(100_000_000), '1.00')
  assert.equal(formatTokenYi(99_999_999), null)
})

test('Token 大数仍保留 M 单位', () => {
  assert.equal(formatTokens(1_234_400_000), '1234.4M')
})
