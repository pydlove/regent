import assert from 'node:assert/strict'
import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const {
  RuntimeManager,
  isShellCompatible,
  releaseRank,
  sha256,
} = require('../runtime-updater.js')

function runtimeDirectory(root, releaseCode, { manifest = true } = {}) {
  const directory = path.join(root, releaseCode)
  fs.mkdirSync(directory, { recursive: true })
  fs.writeFileSync(path.join(directory, 'main.js'), '')
  if (manifest) {
    fs.writeFileSync(path.join(directory, 'runtime-manifest.json'), JSON.stringify({
      schemaVersion: 1,
      releaseCode,
    }))
  }
  return directory
}

test('比较客户端壳和 Runtime 的发布号', () => {
  assert.equal(releaseRank('R100B010'), 100_000_010)
  assert.equal(isShellCompatible('R100B010', 'R100B010'), true)
  assert.equal(isShellCompatible('R100B011', 'R100B010'), false)
  assert.equal(isShellCompatible(null, 'R100B010'), true)
})

test('新 Runtime 首次启动未确认时自动回滚内置版本', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-runtime-rollback-'))
  try {
    const builtin = runtimeDirectory(directory, 'builtin', { manifest: false })
    const userData = path.join(directory, 'user-data')
    const manager = new RuntimeManager({
      userData,
      builtinDirectory: builtin,
      builtinRelease: 'R100B010',
      publicKey: null,
    })
    const downloaded = path.join(userData, 'runtime/releases/R100B011')
    runtimeDirectory(path.dirname(downloaded), 'R100B011')
    manager.writeState({
      schemaVersion: 1,
      current: 'R100B011',
      previous: null,
      pending: true,
      attempts: 1,
    })

    const startup = manager.prepareStartup()

    assert.equal(startup.releaseCode, 'R100B010')
    assert.equal(startup.rolledBack, true)
    assert.equal(manager.readState().current, null)
    assert.equal(manager.readState().failed, 'R100B011')
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('新 Runtime 只有健康确认后才结束待确认状态', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-runtime-health-'))
  try {
    const builtin = runtimeDirectory(directory, 'builtin', { manifest: false })
    const userData = path.join(directory, 'user-data')
    const manager = new RuntimeManager({
      userData,
      builtinDirectory: builtin,
      builtinRelease: 'R100B010',
      publicKey: null,
    })
    runtimeDirectory(path.join(userData, 'runtime/releases'), 'R100B011')
    manager.writeState({
      schemaVersion: 1,
      current: 'R100B011',
      previous: null,
      failed: null,
      pending: true,
      attempts: 0,
    })

    const startup = manager.prepareStartup()

    assert.equal(startup.releaseCode, 'R100B011')
    assert.equal(manager.readState().pending, true)
    assert.equal(manager.readState().attempts, 1)

    manager.markHealthy()

    assert.equal(manager.readState().pending, false)
    assert.equal(manager.readState().attempts, 0)
    assert.equal(manager.readState().current, 'R100B011')
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

test('Runtime 安装校验签名、版本和 SHA-256', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-runtime-install-'))
  try {
    const builtin = runtimeDirectory(directory, 'builtin', { manifest: false })
    const { privateKey, publicKey } = crypto.generateKeyPairSync('ed25519')
    const manager = new RuntimeManager({
      userData: path.join(directory, 'user-data'),
      builtinDirectory: builtin,
      builtinRelease: 'R100B010',
      publicKey: publicKey.export({ type: 'spki', format: 'pem' }),
      platform: 'fixture',
    })
    const archive = path.join(directory, 'runtime.zip')
    const content = Buffer.from('signed-runtime')
    fs.writeFileSync(archive, content)
    manager.extract = (_zip, destination) => {
      fs.writeFileSync(path.join(destination, 'main.js'), '')
      fs.writeFileSync(path.join(destination, 'runtime-manifest.json'), JSON.stringify({
        releaseCode: 'R100B011',
      }))
    }
    const descriptor = {
      releaseCode: 'R100B011',
      minShellRelease: 'R100B010',
      sha256: sha256(content),
      signature: crypto.sign(null, content, privateKey).toString('base64url'),
    }

    const installed = manager.install(archive, descriptor)

    assert.equal(installed, path.join(
      manager.releasesDirectory,
      'R100B011',
    ))
    assert.equal(manager.readState().current, 'R100B011')
    assert.equal(manager.readState().failed, null)
    assert.equal(manager.readState().pending, true)

    assert.throws(() => manager.install(archive, {
      ...descriptor,
      signature: Buffer.from('invalid').toString('base64url'),
    }), /签名校验失败/)
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})
