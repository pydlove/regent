import assert from 'node:assert/strict'
import test from 'node:test'

const { detectDesktopOperatingSystem, detectMobileDownloadPlatform } = await import(
  '../src/utils/mobileDownloadPlatform.ts'
)

test('Android 手机优先推荐 Android 安装包', () => {
  assert.equal(
    detectMobileDownloadPlatform(
      'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36',
    ),
    'android',
  )
})

test('iPhone 与明确 iPad UA 推荐 iOS', () => {
  assert.equal(
    detectMobileDownloadPlatform(
      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)',
    ),
    'ios',
  )
  assert.equal(
    detectMobileDownloadPlatform(
      'Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X)',
    ),
    'ios',
  )
})

test('iPadOS 桌面版 UA 通过触点数识别', () => {
  assert.equal(
    detectMobileDownloadPlatform(
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15',
      5,
    ),
    'ios',
  )
})

test('桌面浏览器不伪造移动平台', () => {
  assert.equal(
    detectMobileDownloadPlatform(
      'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15',
      0,
    ),
    'other',
  )
})

test('Mac 桌面识别为网页版优先，iPadOS 不误判', () => {
  const mac =
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15) AppleWebKit/605.1.15'
  assert.equal(detectDesktopOperatingSystem(mac, 0), 'macos')
  assert.equal(detectDesktopOperatingSystem(mac, 5), 'other')
})
