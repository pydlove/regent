import assert from 'node:assert/strict'
import test from 'node:test'

import {
  artifactSetFromManifest,
  assertReleasePreflight,
  codexPrompt,
  nextVersion,
  normalizeDraft,
  normalizeReleasePreview,
  normalizeReviewedRelease,
  parseVersion,
  publishArguments,
  releaseEvidence,
} from '../lib.mjs'

test('解析发布版本并自动递增构建号', () => {
  assert.deepEqual(parseVersion('Regent_V1.0.3_R100B001'), {
    version: 'Regent_V1.0.3_R100B001',
    desktopVersion: '1.0.3',
    releaseCode: 'R100B001',
  })
  assert.deepEqual(parseVersion('Regent_V1.0.2_R100B004'), {
    version: 'Regent_V1.0.2_R100B004',
    desktopVersion: '1.0.2',
    releaseCode: 'R100B004',
  })
  assert.equal(nextVersion('Regent_V1.0.3_R100B001'), 'Regent_V1.0.3_R100B002')
})

test('拒绝不合法版本号', () => {
  assert.throws(() => parseVersion('1.0.4'), /版本号格式/)
})

test('规范化和校验 Codex 生成内容', () => {
  assert.deepEqual(normalizeReleasePreview({
    summary: ' 本次更新说明 ',
    highlights: [
      { title: ' 自动更新 ', description: ' 下载后自动安装 ' },
      { title: ' 发布中心 ', description: ' 支持预览确认 ' },
      { title: ' Git 标签 ', description: ' 自动创建版本标签 ' },
    ],
  }), {
    summary: '本次更新说明',
    highlights: [
      { title: '自动更新', description: '下载后自动安装' },
      { title: '发布中心', description: '支持预览确认' },
      { title: 'Git 标签', description: '自动创建版本标签' },
    ],
  })
  assert.throws(() => normalizeReviewedRelease({
    summary: '重复标题',
    highlights: [
      { title: '更新', description: '一' },
      { title: '更新', description: '二' },
    ],
  }), /标题重复/)
})

test('Codex 提示包含版本和用户补充要求', () => {
  const prompt = codexPrompt({
    version: 'Regent_V1.0.2_R100B004',
    previousVersion: 'Regent_V1.0.2_R100B003',
    instruction: '突出自动更新',
  })
  assert.match(prompt, /R100B004/)
  assert.match(prompt, /R100B003/)
  assert.match(prompt, /突出自动更新/)
})

test('规范发布草稿并保留部署选项', () => {
  assert.deepEqual(normalizeDraft({
    version: 'Regent_V1.0.2_R100B004',
    summary: ' 本次更新 ',
    highlights: [
      { title: ' 发布草稿 ', description: ' 刷新后仍然保留 ' },
      { title: ' 自动恢复 ', description: ' 避免重复生成 ' },
      { title: ' 人工确认 ', description: ' 刷新后仍需重新勾选 ' },
    ],
    webOnly: true,
    deploy: false,
  }), {
    version: 'Regent_V1.0.2_R100B004',
    desktopVersion: '1.0.2',
    releaseCode: 'R100B004',
    summary: '本次更新',
    highlights: [
      { title: '发布草稿', description: '刷新后仍然保留' },
      { title: '自动恢复', description: '避免重复生成' },
      { title: '人工确认', description: '刷新后仍需重新勾选' },
    ],
    webOnly: true,
    deploy: false,
  })
})

test('正式发布默认创建 Git 标签', () => {
  assert.deepEqual(publishArguments({
    version: 'Regent_V1.0.2_R100B005',
    releaseInfoFile: '/tmp/release-info.json',
    webOnly: true,
    deploy: true,
  }), [
    '--version',
    'Regent_V1.0.2_R100B005',
    '--release-info-file',
    '/tmp/release-info.json',
    '--web-only',
    '--deploy',
  ])
})

test('正式发布前拒绝未提交工作区、重复版本和重复标签', () => {
  assert.equal(assertReleasePreflight({
    dirty: false,
    tagExists: false,
    versionExists: false,
  }), true)
  assert.throws(() => assertReleasePreflight({
    dirty: true,
    tagExists: false,
    versionExists: false,
  }), /未提交改动/)
  assert.throws(() => assertReleasePreflight({
    dirty: false,
    tagExists: true,
    versionExists: false,
  }), /标签已存在/)
  assert.throws(() => assertReleasePreflight({
    dirty: false,
    tagExists: false,
    versionExists: true,
  }), /版本已存在/)
})

test('从发布清单生成不可变产物凭证', () => {
  const manifest = {
    version: 'Regent_V1.0.2_R100B005',
    releaseCode: 'R100B005',
    clientReleaseCode: 'R100B005',
    webOnly: false,
    runtime: {
      file: 'Regent-runtime.zip',
      sizeBytes: 1024,
      sha256: 'a'.repeat(64),
    },
    downloads: {
      macOS: {
        file: 'Regent.dmg',
        sizeBytes: 2048,
        sha256: 'b'.repeat(64),
      },
    },
  }
  assert.deepEqual(artifactSetFromManifest(manifest), [
    { kind: 'macOS', file: 'Regent.dmg', sizeBytes: 2048, sha256: 'b'.repeat(64) },
    { kind: 'runtime', file: 'Regent-runtime.zip', sizeBytes: 1024, sha256: 'a'.repeat(64) },
  ])
  assert.throws(() => artifactSetFromManifest({ downloads: {} }), /没有任何可追溯产物/)
  assert.deepEqual(releaseEvidence({
    version: manifest.version,
    jobId: 'job-1',
    sourceCommit: 'source-sha',
    commitSha: 'tag-sha',
    tagName: manifest.version,
    manifest,
    manifestPath: '/tmp/latest.json',
    manifestSha256: 'c'.repeat(64),
    createdAt: '2026-10-20T12:00:00.000Z',
  }), {
    version: manifest.version,
    jobId: 'job-1',
    releaseCode: 'R100B005',
    channel: 'desktop',
    sourceCommit: 'source-sha',
    commitSha: 'tag-sha',
    tagName: manifest.version,
    manifestPath: '/tmp/latest.json',
    manifestSha256: 'c'.repeat(64),
    artifacts: [
      { kind: 'macOS', file: 'Regent.dmg', sizeBytes: 2048, sha256: 'b'.repeat(64) },
      { kind: 'runtime', file: 'Regent-runtime.zip', sizeBytes: 1024, sha256: 'a'.repeat(64) },
    ],
    createdAt: '2026-10-20T12:00:00.000Z',
  })
})
