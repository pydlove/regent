import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { createInterface } from 'node:readline/promises'
import { fileURLToPath } from 'node:url'

const projectDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const releasesPath = path.join(projectDirectory, 'regent-web/src/content/releases.json')
const desktopPackagePath = path.join(projectDirectory, 'regent-desktop/package.json')
const desktopLockPath = path.join(projectDirectory, 'regent-desktop/package-lock.json')
const desktopReleaseInfoPath = path.join(projectDirectory, 'regent-desktop/release-info.json')
const VERSION_PATTERN = /^feature_V(\d+(?:\.\d+){2})_(R\d+B\d+)$/

function option(name) {
  const inline = process.argv.find((argument) => argument.startsWith(`--${name}=`))
  if (inline) return inline.slice(name.length + 3)
  const index = process.argv.indexOf(`--${name}`)
  return index >= 0 ? process.argv[index + 1] : undefined
}

function flag(name) {
  return process.argv.includes(`--${name}`)
}

function run(command, args, options = {}) {
  console.log(`\n$ ${command} ${args.join(' ')}`)
  const result = spawnSync(command, args, {
    cwd: options.cwd || projectDirectory,
    stdio: 'inherit',
    env: { ...process.env, ...options.env },
  })
  if (result.error) throw result.error
  if (result.status !== 0) {
    throw new Error(`${command} 执行失败，退出码 ${result.status}`)
  }
}

function capture(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd || projectDirectory,
    encoding: 'utf8',
    env: { ...process.env, ...options.env },
  })
  if (result.error) throw result.error
  if (result.status !== 0) {
    throw new Error(`${command} 执行失败：${(result.stderr || '').trim()}`)
  }
  return result.stdout.trim()
}

function nextVersion(version) {
  const match = version.match(/^(.*_R\d+B)(\d+)$/)
  if (!match) throw new Error(`无法自动递增版本号：${version}`)
  return `${match[1]}${String(Number(match[2]) + 1).padStart(match[2].length, '0')}`
}

function today() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}

function writeJson(file, value) {
  fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`, 'utf8')
}

function preflightTagPush(tagName) {
  try {
    capture('git', [
      'push',
      '--dry-run',
      'origin',
      `HEAD:refs/heads/regent-release-auth-${process.pid}-${Date.now()}`,
    ])
  } catch (error) {
    throw new Error(
      `无法推送 Git 标签 ${tagName}：GitHub 凭据不可用。`
      + `请先执行 gh auth login && gh auth setup-git，再重新发布。`
      + `\n${error.message}`,
    )
  }
}

function buildReleaseNotes(notes) {
  const items = notes
    .split(/[;；\n]+/)
    .map((item) => item.trim().replace(/^[-*]\s*/, ''))
    .filter(Boolean)
  const normalized = items.length > 0 ? items : ['版本更新与问题修复']
  const usedTitles = new Set()
  const highlights = normalized.map((description, index) => {
    const separator = description.search(/[：:]/)
    const candidate = separator > 0 && separator <= 20
      ? description.slice(0, separator).trim()
      : description.slice(0, 18).trim()
    let title = candidate || `更新 ${index + 1}`
    if (title.length < description.length && separator <= 0) title += '…'
    if (usedTitles.has(title)) title = `${title} ${index + 1}`
    usedTitles.add(title)
    return { title, description }
  })
  return {
    summary: normalized.join('；') + (/[。！？!?]$/.test(normalized.at(-1)) ? '' : '。'),
    highlights,
  }
}

function readReleaseInfo(file) {
  const value = JSON.parse(fs.readFileSync(file, 'utf8'))
  if (typeof value.summary !== 'string' || value.summary.trim() === '') {
    throw new Error('版本内容缺少 summary')
  }
  if (!Array.isArray(value.highlights) || value.highlights.length === 0) {
    throw new Error('版本内容至少需要一个更新项')
  }
  if (value.highlights.length > 12) {
    throw new Error('版本更新项不能超过 12 条')
  }
  const titles = new Set()
  const highlights = value.highlights.map((item, index) => {
    const title = typeof item?.title === 'string' ? item.title.trim() : ''
    const description = typeof item?.description === 'string' ? item.description.trim() : ''
    if (!title || !description) {
      throw new Error(`第 ${index + 1} 个更新项缺少标题或说明`)
    }
    if (titles.has(title)) throw new Error(`更新项标题重复：${title}`)
    titles.add(title)
    return { title, description }
  })
  return { summary: value.summary.trim(), highlights }
}

function ensureDependencies(directory) {
  if (fs.existsSync(path.join(directory, 'node_modules'))) return
  run('npm', ['install', '--registry=https://registry.npmmirror.com', '--strict-ssl=false'], {
    cwd: directory,
  })
}

function deploymentEnvironment() {
  const environment = { ...process.env }
  try {
    const relayEnv = fs.readFileSync(
      path.join(process.env.HOME || '', '.regent/relay-connector.env'),
      'utf8',
    )
    const serverUrl = relayEnv.match(/^REGENT_RELAY_SERVER_URL=(.+)$/m)?.[1]?.trim()
    if (serverUrl) environment.REGENT_SERVER_IP ||= new URL(serverUrl).hostname
  } catch {
    // 没有中继配置时仍允许用户在环境变量中显式提供部署参数。
  }
  environment.REGENT_SERVER_USER ||= 'root'
  environment.REGENT_SSH_PORT ||= '22'
  const defaultKey = path.join(process.env.HOME || '', '.ssh/id_rsa')
  if (!environment.REGENT_SSH_KEY && fs.existsSync(defaultKey)) {
    environment.REGENT_SSH_KEY = defaultKey
  }
  return environment
}

const releases = JSON.parse(fs.readFileSync(releasesPath, 'utf8'))
const currentVersion = releases.releases[0]?.version
if (!currentVersion || !VERSION_PATTERN.test(currentVersion)) {
  throw new Error(`当前版本号不合法：${currentVersion || '空'}`)
}

let version = option('version')
let notes = option('notes')
let deploy = flag('deploy')

if (!version) {
  const prompt = createInterface({ input: process.stdin, output: process.stdout })
  try {
    const suggested = nextVersion(currentVersion)
    version = (await prompt.question(`新版本号 [${suggested}]: `)).trim() || suggested
    notes = (await prompt.question('更新说明（多条用；分隔）[版本更新与问题修复]: ')).trim()
      || '版本更新与问题修复'
    const deployAnswer = (await prompt.question('是否同时部署公网前端？[y/N]: '))
      .trim()
      .toLowerCase()
    deploy = deployAnswer === 'y' || deployAnswer === 'yes'
  } finally {
    prompt.close()
  }
}

notes ||= '版本更新与问题修复'
const match = version.match(VERSION_PATTERN)
if (!match) {
  throw new Error('版本号格式应为 feature_V1.0.2_R100B003')
}
const desktopVersion = option('desktop-version') || match[1]
const releaseCode = option('release-code') || match[2]
if (releases.releases.some((release) => release.version === version) && !flag('force')) {
  throw new Error(
    `版本 ${version} 已存在。重复发布同一版本不会触发客户端更新；`
    + `请改用 ${nextVersion(version)}，确需覆盖安装包请加 --force。`,
  )
}

const releaseInfoFile = option('release-info-file')
const releaseNotes = releaseInfoFile
  ? readReleaseInfo(releaseInfoFile)
  : buildReleaseNotes(notes)
if (releaseInfoFile) notes = releaseNotes.summary
const releaseEntry = {
  version,
  label: '版本更新',
  releasedAt: today(),
  summary: releaseNotes.summary,
  highlights: releaseNotes.highlights,
}

let tagCommit = null
const tagName = version
if (!flag('no-tag')) {
  if (capture('git', ['status', '--porcelain']) !== '' && !flag('allow-dirty')) {
    throw new Error(
      '工作区有未提交改动。发布版本需要先提交代码，确保 Git 标签与安装包一致；'
      + '仅重新生成安装包可加 --no-tag。',
    )
  }
  const existingTag = spawnSync('git', ['rev-parse', '-q', '--verify', `refs/tags/${tagName}`], {
    cwd: projectDirectory,
    stdio: 'ignore',
  })
  if (existingTag.status === 0) {
    throw new Error(
      `Git 标签 ${tagName} 已存在。标签不可重复创建；`
      + '如只是重新生成安装包，请加 --no-tag。',
    )
  }
  if (flag('push-tag') && !flag('dry-run')) preflightTagPush(tagName)
  tagCommit = capture('git', ['rev-parse', 'HEAD'])
}

const summary = {
  version,
  releaseCode,
  desktopVersion,
  tag: flag('no-tag') ? null : tagName,
  tagCommit,
  notes,
  summary: releaseNotes.summary,
  highlights: releaseNotes.highlights,
  deploy,
}
if (flag('dry-run')) {
  console.log(JSON.stringify(summary, null, 2))
  process.exit(0)
}

const originals = new Map()
for (const file of [releasesPath, desktopPackagePath, desktopLockPath, desktopReleaseInfoPath]) {
  originals.set(file, fs.readFileSync(file, 'utf8'))
}

let published = false
try {
  releases.releases = [
    releaseEntry,
    ...releases.releases.filter((release) => release.version !== version),
  ]
  writeJson(releasesPath, releases)

  const desktopPackage = JSON.parse(fs.readFileSync(desktopPackagePath, 'utf8'))
  desktopPackage.version = desktopVersion
  writeJson(desktopPackagePath, desktopPackage)

  const desktopLock = JSON.parse(fs.readFileSync(desktopLockPath, 'utf8'))
  desktopLock.version = desktopVersion
  if (desktopLock.packages?.['']) desktopLock.packages[''].version = desktopVersion
  writeJson(desktopLockPath, desktopLock)

  writeJson(desktopReleaseInfoPath, {
    version: desktopVersion,
    productVersion: version,
    releaseCode,
    releasedAt: releaseEntry.releasedAt,
  })

  const webDirectory = path.join(projectDirectory, 'regent-web')
  const desktopDirectory = path.join(projectDirectory, 'regent-desktop')
  ensureDependencies(webDirectory)
  ensureDependencies(desktopDirectory)

  console.log(`\n发布 ${version}（${releaseCode}）`)
  run('npm', ['run', 'typecheck'], { cwd: webDirectory })
  run('npm', ['test'], { cwd: webDirectory })
  run('npm', ['run', 'build'], { cwd: webDirectory })
  run('npm', ['run', 'build:release'], {
    cwd: desktopDirectory,
    env: { REGENT_RELEASE_CODE: releaseCode },
  })

  const macos = path.join(
    desktopDirectory,
    'dist',
    `Regent-${desktopVersion}-${releaseCode}-macOS.dmg`,
  )
  const windows = path.join(
    desktopDirectory,
    'dist',
    `Regent-${desktopVersion}-${releaseCode}-Windows-x64.zip`,
  )
  run('node', [
    'scripts/publish-release.mjs',
    '--version',
    version,
    '--code',
    releaseCode,
    '--notes',
    notes,
    '--macos',
    macos,
    '--windows',
    windows,
  ])
  published = true

  if (tagCommit) {
    run('git', [
      'add',
      releasesPath,
      desktopPackagePath,
      desktopLockPath,
      desktopReleaseInfoPath,
    ])
    if (capture('git', ['status', '--porcelain']) !== '') {
      run('git', ['commit', '-m', `chore(release): 发布 ${version}`])
    }
    tagCommit = capture('git', ['rev-parse', 'HEAD'])
  }

  if (deploy) {
    run(path.join(projectDirectory, 'deploy/remote/deploy.sh'), [], {
      env: deploymentEnvironment(),
    })
  }

  if (tagCommit) {
    run('git', [
      'tag',
      '-a',
      tagName,
      tagCommit,
      '-m',
      `${tagName}\n\n${releaseNotes.summary}`,
    ])
    if (flag('push-tag')) {
      run('git', ['push', 'origin', tagName])
    }
    console.log(`Git 标签：${tagName} -> ${tagCommit.slice(0, 8)}`)
  }

  console.log('\n发布完成')
  console.log(`版本：${version}`)
  console.log(`发布码：${releaseCode}`)
  console.log(`macOS：${macos}`)
  console.log(`Windows：${windows}`)
  console.log(`发布清单：${path.join(process.env.HOME || '', '.regent/downloads/latest.json')}`)
} catch (error) {
  if (!published) {
    for (const [file, content] of originals) fs.writeFileSync(file, content, 'utf8')
    console.error('\n发布失败，已恢复版本文件。')
  } else {
    console.error('\n安装包已发布，但后续步骤失败。')
  }
  throw error
}
