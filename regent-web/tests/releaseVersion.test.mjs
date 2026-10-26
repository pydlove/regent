import assert from 'node:assert/strict'
import test from 'node:test'

import {
  compareReleaseVersion,
  isNewerRelease,
  shortReleaseVersion,
} from '../src/utils/releaseVersion.ts'

test('版本比较优先比较产品版本，再比较 R 和 B', () => {
  assert.equal(
    compareReleaseVersion('Regent_V1.0.3_R100B001', 'Regent_V1.0.2_R100B012'),
    1,
  )
  assert.equal(
    compareReleaseVersion('Regent_V1.0.3_R100B002', 'Regent_V1.0.3_R100B001'),
    1,
  )
  assert.equal(
    compareReleaseVersion('Regent_V1.0.3_R100B001', 'Regent_V1.0.3_R100B001'),
    0,
  )
})

test('低版本清单不会再触发刷新提示', () => {
  assert.equal(
    isNewerRelease('Regent_V1.0.2_R100B012', 'Regent_V1.0.3_R100B001'),
    false,
  )
})

test('紧凑版本号保留完整 R 发布码', () => {
  assert.equal(
    shortReleaseVersion('Regent_V1.0.4_R100B013'),
    'V1.0.4 · R100B013',
  )
  assert.equal(shortReleaseVersion('V1.0.4'), 'V1.0.4')
})
