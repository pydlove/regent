import assert from 'node:assert/strict'
import test from 'node:test'

const {
  isWindowsClient,
  preferredDownloadOrder,
  recommendedDownloadPlatform,
} = await import('../src/utils/downloadPlatform.ts')

test('Windows 客户端优先看到 Windows 安装包', () => {
  assert.equal(
    isWindowsClient(
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140 Safari/537.36',
    ),
    true,
  )
  assert.deepEqual(preferredDownloadOrder(true), ['windows', 'macos'])
  assert.equal(recommendedDownloadPlatform(true), 'windows')
})

test('macOS 与未知客户端保持 macOS 优先', () => {
  assert.equal(
    isWindowsClient(
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15',
    ),
    false,
  )
  assert.deepEqual(preferredDownloadOrder(false), ['macos', 'windows'])
  assert.equal(recommendedDownloadPlatform(false), 'macos')
  assert.equal(isWindowsClient(null), false)
})
