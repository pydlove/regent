export const VERSION_PATTERN = /^Regent_V(\d+(?:\.\d+){2})_(R\d+B\d+)$/

export function parseVersion(version) {
  const value = typeof version === 'string' ? version.trim() : ''
  const match = value.match(VERSION_PATTERN)
  if (!match) {
    throw new Error('版本号格式应为 Regent_V1.0.3_R100B001')
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

export function normalizeDraft(value) {
  if (!value || typeof value !== 'object') {
    throw new Error('缺少发布草稿')
  }
  const parsed = parseVersion(value.version)
  const content = normalizeReviewedRelease(value)
  return {
    ...parsed,
    ...content,
    webOnly: value.webOnly === true,
    deploy: value.deploy !== false,
  }
}

export function publishArguments({ version, releaseInfoFile, deploy, webOnly = false }) {
  const args = [
    '--version',
    version,
    '--release-info-file',
    releaseInfoFile,
  ]
  if (webOnly === true) args.push('--web-only')
  if (deploy === true) args.push('--deploy')
  return args
}

export function assertReleasePreflight({ dirty, tagExists, versionExists }) {
  if (versionExists) {
    throw new Error('该版本已存在，请递增构建号')
  }
  if (dirty) {
    throw new Error('工作区有未提交改动，正式发布前必须先提交代码')
  }
  if (tagExists) {
    throw new Error('该版本的 Git 标签已存在，不能重复创建正式发布')
  }
  return true
}

export function artifactSetFromManifest(manifest) {
  if (!manifest || typeof manifest !== 'object') {
    throw new Error('发布清单为空')
  }
  const artifacts = []
  for (const [platform, asset] of Object.entries(manifest.downloads || {})) {
    artifacts.push(normalizeArtifact(platform, asset))
  }
  if (manifest.runtime) {
    artifacts.push(normalizeArtifact('runtime', manifest.runtime))
  }
  if (artifacts.length === 0) {
    throw new Error('发布清单没有任何可追溯产物')
  }
  return artifacts
}

export function releaseEvidence({
  version,
  jobId,
  sourceCommit,
  commitSha,
  tagName,
  manifest,
  manifestPath,
  manifestSha256,
  createdAt,
}) {
  if (!version || !jobId || !commitSha) {
    throw new Error('发布凭证缺少版本、任务或提交信息')
  }
  return {
    version,
    jobId,
    releaseCode: manifest?.releaseCode || manifest?.clientReleaseCode || null,
    channel: manifest?.webOnly === true ? 'web-only' : 'desktop',
    sourceCommit: sourceCommit || null,
    commitSha,
    tagName: tagName || null,
    manifestPath: manifestPath || null,
    manifestSha256: manifestSha256 || null,
    artifacts: artifactSetFromManifest(manifest),
    createdAt: createdAt || new Date().toISOString(),
  }
}

function normalizeArtifact(kind, asset) {
  const file = typeof asset?.file === 'string' ? asset.file.trim() : ''
  const sha256 = typeof asset?.sha256 === 'string' ? asset.sha256.trim().toLowerCase() : ''
  const sizeBytes = Number(asset?.sizeBytes)
  if (!file || !/^[a-f0-9]{64}$/.test(sha256) || !Number.isSafeInteger(sizeBytes) || sizeBytes < 0) {
    throw new Error(`发布清单中的 ${kind} 产物缺少有效文件、大小或摘要`)
  }
  return { kind, file, sizeBytes, sha256 }
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
