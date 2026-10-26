import assert from 'node:assert/strict'
import test from 'node:test'

const { resolveRouterScroll } = await import('../src/utils/routerScroll.ts')

test('新页面导航回到顶部，避免沿用上一个页面的滚动位置', () => {
  assert.deepEqual(resolveRouterScroll(null, ''), { left: 0, top: 0 })
})

test('前进后退保留浏览器记录的位置', () => {
  assert.deepEqual(
    resolveRouterScroll({ left: 0, top: 480 }, ''),
    { left: 0, top: 480 },
  )
})

test('带锚点的导航仍定位到锚点', () => {
  assert.deepEqual(
    resolveRouterScroll(null, '#install-android'),
    { el: '#install-android', top: 16, behavior: 'smooth' },
  )
})
