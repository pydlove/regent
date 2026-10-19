export const VERSION_PATTERN = /^feature_V(\d+(?:\.\d+){2})_(R\d+B\d+)$/

export function parseVersion(version) {
  const value = typeof version === 'string' ? version.trim() : ''
  const match = value.match(VERSION_PATTERN)
  if (!match) {
    throw new Error('版本号格式应为 feature_V1.0.2_R100B004')
  }
  return {
    version: value,
    desktopVersion: match[1],
    releaseCode: match[2],
  }
}

export function nextVersion(version) {
  const match = String(version).match(/^(.*_R\d+B)(\d+)$/)
  if (!match) throw new Error(`无法递增版本号：${version}`)
  return `${match[1]}${String(Number(match[2]) + 1).padStart(match[2].length, '0')}`
}

export function normalizeReleasePreview(value) {
  if (!value || typeof value !== 'object') {
    throw new Error('Codex 没有返回版本内容')
  }
  const summary = typeof value.summary === 'string' ? value.summary.trim() : ''
  if (!summary) throw new Error('Codex 返回的版本摘要为空')
  if (!Array.isArray(value.highlights) || value.highlights.length === 0) {
    throw new Error('Codex 没有返回版本更新项')
  }
  if (value.highlights.length > 8) {
    throw new Error('版本更新项不能超过 8 条')
  }
  const titles = new Set()
  const highlights = value.highlights.map((item, index) => {
    const title = typeof item?.title === 'string' ? item.title.trim() : ''
    const description = typeof item?.description === 'string' ? item.description.trim() : ''
    if (!title || !description) {
      throw new Error(`第 ${index + 1} 个更新项缺少标题或说明`)
    }
    if (titles.has(title)) {
      throw new Error(`更新项标题重复：${title}`)
    }
    titles.add(title)
    return { title, description }
  })
  return { summary, highlights }
}

export function normalizeReviewedRelease(value) {
  if (!value || typeof value !== 'object') {
    throw new Error('缺少审核后的版本内容')
  }
  return normalizeReleasePreview(value)
}

export function codexPrompt({ version, previousVersion, instruction }) {
  return `
你是 Regent 项目的发布经理。只读检查当前仓库，生成面向用户的版本发布说明。

新版本：${version}
上一个版本：${previousVersion}
补充要求：${instruction || '无'}

请检查：
1. git status、git diff 和最近的 git log；
2. regent-web/src/content/releases.json 的历史版本；
3. README、TECH_STACK 和本次新增或修改的功能、修复、运维变化。

输出简体中文，面向用户，不要写提交哈希，不要夸大，不要修改任何文件。
summary 用一到两句话概括本版本；highlights 输出 3 到 6 条具体更新，
每条 title 简短明确，description 说明用户能感知的变化。
只输出符合 schema 的 JSON，不要 Markdown。
`.trim()
}

export function codexSchema() {
  return {
    type: 'object',
    additionalProperties: false,
    required: ['summary', 'highlights'],
    properties: {
      summary: { type: 'string', minLength: 1, maxLength: 240 },
      highlights: {
        type: 'array',
        minItems: 3,
        maxItems: 6,
        items: {
          type: 'object',
          additionalProperties: false,
          required: ['title', 'description'],
          properties: {
            title: { type: 'string', minLength: 1, maxLength: 40 },
            description: { type: 'string', minLength: 1, maxLength: 240 },
          },
        },
      },
    },
  }
}
