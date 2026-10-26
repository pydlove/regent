import assert from 'node:assert/strict'
import test from 'node:test'

const { APP_VERSION, CURRENT_RELEASE, RELEASES } = await import('../src/content/releases.ts')

test('版本号与发布说明保持同一口径', () => {
  assert.match(APP_VERSION, /^Regent_V\d+(?:\.\d+){2}_R\d+B\d+$/)
  assert.equal(CURRENT_RELEASE.version, APP_VERSION)
  assert.equal(RELEASES[0], CURRENT_RELEASE)
  assert.match(CURRENT_RELEASE.releasedAt, /^\d{4}-\d{2}-\d{2}$/)
})

test('当前与历史版本都包含可展示且不重复的更新项', () => {
  assert.ok(RELEASES.length > 1)
  assert.equal(
    new Set(RELEASES.map((release) => release.version)).size,
    RELEASES.length,
  )

  for (const release of RELEASES) {
    const titles = release.highlights.map((item) => item.title)

    assert.match(release.version, /^Regent_V\d+(?:\.\d+){2}_R\d+B\d+$/)
    assert.match(release.releasedAt, /^\d{4}-\d{2}-\d{2}$/)
    assert.ok(release.summary.trim().length > 0)
    assert.ok(titles.length > 0)
    assert.equal(new Set(titles).size, titles.length)
  }
})
