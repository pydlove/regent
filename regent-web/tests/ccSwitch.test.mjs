import assert from 'node:assert/strict'
import test from 'node:test'

const {
  detectCcSwitchPlatform,
  parseCcSwitchRelease,
} = await import('../src/api/ccSwitch.ts')

test('CC Switch 按当前系统识别快捷下载入口', () => {
  assert.equal(detectCcSwitchPlatform('Mozilla/5.0 (Windows NT 10.0; Win64; x64)'), 'windows')
  assert.equal(detectCcSwitchPlatform('Mozilla/5.0 (X11; Linux x86_64)'), 'linux')
  assert.equal(detectCcSwitchPlatform('Mozilla/5.0 (Macintosh; Intel Mac OS X)'), 'macos')
})

test('CC Switch 从官方发布信息中选出各平台安装包', () => {
  const release = parseCcSwitchRelease({
    tag_name: 'v4.0.16',
    html_url: 'https://github.com/farion1231/cc-switch/releases/tag/v4.0.16',
    published_at: '2026-10-19T10:46:34Z',
    assets: [
      { name: 'CC-Switch-v4.0.16-macOS.dmg', browser_download_url: 'https://example.com/mac.dmg', size: 10 },
      { name: 'CC-Switch-v4.0.16-Windows.msi', browser_download_url: 'https://example.com/windows.msi', size: 20 },
      { name: 'CC-Switch-v4.0.16-Windows-Portable.zip', browser_download_url: 'https://example.com/windows.zip', size: 21 },
      { name: 'CC-Switch-v4.0.16-Windows-arm64.msi', browser_download_url: 'https://example.com/windows-arm64.msi', size: 22 },
      { name: 'CC-Switch-v4.0.16-Linux-x86_64.AppImage', browser_download_url: 'https://example.com/linux.AppImage', size: 30 },
    ],
  })

  assert.equal(release.version, '4.0.16')
  assert.equal(release.assets.macos?.url, 'https://example.com/mac.dmg')
  assert.equal(release.assets.windowsInstaller?.url, 'https://example.com/windows.msi')
  assert.equal(release.assets.windowsPortable?.url, 'https://example.com/windows.zip')
  assert.equal(release.assets.linuxAppImage?.url, 'https://example.com/linux.AppImage')
})

test('缺少安装包时保留版本信息而不是伪造下载地址', () => {
  const release = parseCcSwitchRelease({
    tag_name: 'v4.0.16',
    assets: [{ name: 'latest.json', browser_download_url: 'https://example.com/latest.json' }],
  })
  assert.deepEqual(release.assets, {
    macos: null,
    windowsInstaller: null,
    windowsPortable: null,
    linuxAppImage: null,
  })
  assert.equal(release.releaseUrl, 'https://github.com/farion1231/cc-switch/releases/latest')
})
