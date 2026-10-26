import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'

import { ReleaseStore } from '../store.mjs'

function withStore(run) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-release-store-'))
  const store = new ReleaseStore(path.join(directory, 'release-center.db'))
  try {
    run(store)
  } finally {
    store.close()
    fs.rmSync(directory, { recursive: true, force: true })
  }
}

test('发布任务持久化后可按最新时间和标识查询', () => {
  withStore((store) => {
    store.upsertJob({
      id: 'job-1',
      version: 'Regent_V1.0.3_R100B001',
      kind: 'publish',
      status: 'running',
      logs: ['开始发布'],
      metadata: { sourceCommit: 'abc' },
      createdAt: '2026-10-20T10:00:00.000Z',
      updatedAt: '2026-10-20T10:01:00.000Z',
    })
    store.upsertJob({
      id: 'job-2',
      version: 'Regent_V1.0.3_R100B002',
      kind: 'server-deploy',
      status: 'success',
      logs: ['部署完成'],
      createdAt: '2026-10-20T11:00:00.000Z',
      updatedAt: '2026-10-20T11:01:00.000Z',
    })

    assert.equal(store.getJob('job-1').logs[0], '开始发布')
    assert.equal(store.getLastJob().id, 'job-2')
    assert.deepEqual(store.listJobs().map((job) => job.id), ['job-2', 'job-1'])
  })
})

test('启动恢复把遗留执行中任务标记为中断', () => {
  withStore((store) => {
    store.upsertJob({
      id: 'job-running',
      version: 'Regent_V1.0.3_R100B003',
      kind: 'publish',
      status: 'running',
      logs: ['构建中'],
      createdAt: '2026-10-20T10:00:00.000Z',
      updatedAt: '2026-10-20T10:01:00.000Z',
    })

    const interrupted = store.interruptRunningJobs(
      '发布中心重启，任务已中断',
      '2026-10-20T10:02:00.000Z',
    )

    assert.equal(interrupted, 1)
    assert.equal(store.getJob('job-running').status, 'interrupted')
    assert.match(store.getJob('job-running').error, /重启/)
  })
})

test('发布凭证和部署记录持久化', () => {
  withStore((store) => {
    store.saveRelease({
      version: 'Regent_V1.0.3_R100B004',
      jobId: 'job-release',
      releaseCode: 'R100B004',
      channel: 'desktop',
      sourceCommit: 'source',
      commitSha: 'tag-commit',
      tagName: 'Regent_V1.0.3_R100B004',
      manifestPath: '/tmp/latest.json',
      manifestSha256: 'a'.repeat(64),
      artifacts: [
        { kind: 'macOS', file: 'Regent.dmg', sizeBytes: 10, sha256: 'b'.repeat(64) },
      ],
      createdAt: '2026-10-20T12:00:00.000Z',
    })
    store.saveDeployment({
      id: 'deployment-1',
      jobId: 'job-deploy',
      environment: 'production',
      targetUrl: 'https://example.com',
      status: 'success',
      createdAt: '2026-10-20T12:10:00.000Z',
      updatedAt: '2026-10-20T12:11:00.000Z',
    })

    assert.equal(store.getRelease('Regent_V1.0.3_R100B004').commitSha, 'tag-commit')
    assert.equal(store.listReleases()[0].artifacts[0].file, 'Regent.dmg')
    assert.equal(store.listDeployments()[0].environment, 'production')
  })
})
