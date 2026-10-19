import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

function option(name) {
  const index = process.argv.indexOf(`--${name}`)
  return index >= 0 ? process.argv[index + 1] : undefined
}

const version = option('version')
if (!version) {
  console.error('用法：node scripts/publish-release.mjs --version <版本> [--code R100B003] [--notes 说明]')
  console.error('       [--min-shell-version 1.0.0] [--macos /path/Regent.dmg] [--windows /path/Regent.exe]')
  process.exit(2)
}

const outputDirectory = path.resolve(
  option('dir') || path.join(os.homedir(), '.regent', 'downloads'),
)
fs.mkdirSync(outputDirectory, { recursive: true })

const sources = {
  macos: option('macos'),
  windows: option('windows'),
}
const downloads = {}

for (const [platform, source] of Object.entries(sources)) {
  if (!source) continue
  const sourcePath = path.resolve(source)
  if (!fs.statSync(sourcePath, { throwIfNoEntry: false })?.isFile()) {
    console.error(`安装包不存在：${sourcePath}`)
    process.exit(2)
  }
  const filename = path.basename(sourcePath)
  if (filename.includes('/') || filename.includes('\\') || filename.includes('..')) {
    console.error(`安装包文件名不安全：${filename}`)
    process.exit(2)
  }
  const target = path.join(outputDirectory, filename)
  if (sourcePath !== target) fs.copyFileSync(sourcePath, target)
  const content = fs.readFileSync(target)
  downloads[platform] = {
    file: filename,
    sizeBytes: content.byteLength,
    sha256: crypto.createHash('sha256').update(content).digest('hex'),
  }
  console.log(`${platform}: ${filename} (${content.byteLength} bytes)`)
}

if (Object.keys(downloads).length === 0) {
  console.error('至少提供 --macos 或 --windows 安装包')
  process.exit(2)
}

const manifest = {
  version,
  releaseCode: option('code') || null,
  releasedAt: new Date().toISOString(),
  minShellVersion: option('min-shell-version') || null,
  notes: option('notes') || null,
  downloads,
}
const manifestPath = path.join(outputDirectory, 'latest.json')
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n', 'utf8')
console.log(`manifest: ${manifestPath}`)
console.log('无需 CDN：后端会通过 /api/release/latest 和 /api/release/download/{platform} 提供。')
