import assert from 'node:assert/strict'
import test from 'node:test'

import {
  codexPrompt,
  nextVersion,
  normalizeReleasePreview,
  normalizeReviewedRelease,
  parseVersion,
} from '../lib.mjs'

test('解析发布版本并自动递增构建号', () => {
  assert.deepEqual(parseVersion('feature_V1.0.2_R100B004'), {
    version: 'feature_V1.0.2_R100B004',
    desktopVersion: '1.0.2',
    releaseCode: 'R100B004',
  })
  assert.equal(nextVersion('feature_V1.0.2_R100B004'), 'feature_V1.0.2_R100B005')
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
    version: 'feature_V1.0.2_R100B004',
    previousVersion: 'feature_V1.0.2_R100B003',
    instruction: '突出自动更新',
  })
  assert.match(prompt, /R100B004/)
  assert.match(prompt, /R100B003/)
  assert.match(prompt, /突出自动更新/)
})
