import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { signRuntime } from '../regent-desktop/scripts/runtime-signing.mjs'

function option(name) {
  const index = process.argv.indexOf(`--${name}`)
  return index >= 0 ? process.argv[index + 1] : undefined
}

function flag(name) {
  return process.argv.includes(`--${name}`)
}

const version = option('version')
const webOnly = flag('web-only')
if (!version) {
  console.error('用法：node scripts/publish-release.mjs --version <版本> [--code R100B003] [--notes 说明]')
  console.error('       [--min-shell-version 1.0.0] [--min-shell-release R100B010]')
  console.error('       [--runtime /path/Regent-runtime.zip] [--macos /path/Regent.dmg] [--windows /path/Regent.exe]')
  console.error('       [--android /path/Regent-Mobile.apk]')
  console.error('       [--web-only --client-code R100B011]')
  process.exit(2)
}

const outputDirectory = path.resolve(
  option('dir') || path.join(os.homedir(), '.regent', 'downloads'),
)
fs.mkdirSync(outputDirectory, { recursive: true })

const sources = {
  macos: option('macos'),
  windows: option('windows'),
  android: option('android'),
}
const manifestPath = path.join(outputDirectory, 'latest.json')
const existingManifest = fs.existsSync(manifestPath)
  ? JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
  : null
const releaseCode = option('code') || null
const clientReleaseCode = option('client-code')
  || (webOnly ? existingManifest?.releaseCode : releaseCode)
if (!clientReleaseCode) {
  console.error('发布清单缺少客户端发布码，请传入 --client-code')
  process.exit(2)
}
if (webOnly && option('runtime')) {
  console.error('--web-only 不能同时发布 Runtime')
  process.exit(2)
}
const downloads = {}
for (const [platform, asset] of Object.entries(existingManifest?.downloads || {})) {
  const filename = typeof asset?.file === 'string' ? asset.file : null
  if (filename && fs.existsSync(path.join(outputDirectory, filename))) {
    downloads[platform] = asset
  }
}

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

let runtime = webOnly ? null : existingManifest?.runtime || null
const runtimeSource = option('runtime')
if (runtimeSource) {
  const sourcePath = path.resolve(runtimeSource)
  if (!fs.statSync(sourcePath, { throwIfNoEntry: false })?.isFile()) {
    console.error(`Runtime 包不存在：${sourcePath}`)
    process.exit(2)
  }
  const filename = path.basename(sourcePath)
  const target = path.join(outputDirectory, filename)
  if (sourcePath !== target) fs.copyFileSync(sourcePath, target)
  const content = fs.readFileSync(target)
  runtime = {
    version,
    releaseCode,
    file: filename,
    minShellRelease: option('min-shell-release') || null,
    sizeBytes: content.byteLength,
    sha256: crypto.createHash('sha256').update(content).digest('hex'),
    signature: signRuntime(content),
  }
  console.log(`runtime: ${filename} (${content.byteLength} bytes)`)
}

if (Object.keys(downloads).length === 0 && !runtime && !webOnly) {
  console.error('至少提供 --runtime、--macos、--windows 安装包，或使用 --web-only')
  process.exit(2)
}

const manifest = {
  version,
  releaseCode: clientReleaseCode,
  clientReleaseCode,
  webOnly,
  releasedAt: new Date().toISOString(),
  minShellVersion: option('min-shell-version') || null,
  notes: option('notes') || null,
  runtime,
  downloads,
}
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n', 'utf8')
console.log(`manifest: ${manifestPath}`)
console.log(
  webOnly
    ? `纯网页发布：客户端继续使用 ${clientReleaseCode}，不弹出桌面升级框。`
    : `客户端发布码：${clientReleaseCode}`,
)
console.log('无需 CDN：版本清单由 /api/release/latest 提供，安装包由 /downloads/<文件名> 直接下载。')
console.log('公网发布：再运行 deploy/remote/deploy.sh，会把 ~/.regent/downloads 同步到中继服务器。')
