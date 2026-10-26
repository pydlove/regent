import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const {
  readSkippedRelease,
  writeSkippedRelease,
} = require('../update-preferences.js')

test('跳过版本在应用重启后仍可读取', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-update-preferences-'))
  const file = path.join(directory, 'update-preferences.json')
  try {
    assert.equal(writeSkippedRelease(file, 'R100B020'), 'R100B020')
    assert.equal(readSkippedRelease(file), 'R100B020')
    assert.equal(writeSkippedRelease(file, 'R100B021'), 'R100B021')
    assert.equal(readSkippedRelease(file), 'R100B021')
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('无效或损坏的更新偏好按未跳过处理', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-update-preferences-'))
  const file = path.join(directory, 'update-preferences.json')
  try {
    fs.writeFileSync(file, '{broken', 'utf8')
    assert.equal(readSkippedRelease(file), null)
    fs.writeFileSync(file, JSON.stringify({ skippedRelease: 'latest' }), 'utf8')
    assert.equal(readSkippedRelease(file), null)
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})
