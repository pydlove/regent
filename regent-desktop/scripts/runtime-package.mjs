import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'

export const RUNTIME_FILES = [
  'main.js',
  'runtime-updater.js',
  'device-agent.js',
  'server-url.js',
  'update-policy.js',
  'update-preferences.js',
  'preload.js',
  'update-preload.js',
  'release-info.json',
  'shell',
]

export function stageRuntime(projectDirectory, destination, {
  releaseCode,
  minShellRelease,
} = {}) {
  fs.rmSync(destination, { recursive: true, force: true })
  fs.mkdirSync(destination, { recursive: true })
  for (const filename of RUNTIME_FILES) {
    const source = path.join(projectDirectory, filename)
    if (!fs.existsSync(source)) {
      throw new Error(`Runtime 文件不存在：${source}`)
    }
    fs.cpSync(source, path.join(destination, filename), { recursive: true })
  }
  fs.writeFileSync(path.join(destination, 'runtime-manifest.json'), `${JSON.stringify({
    schemaVersion: 1,
    releaseCode: releaseCode || null,
    minShellRelease: minShellRelease || null,
    builtAt: new Date().toISOString(),
  }, null, 2)}\n`, 'utf8')
  return destination
}

export function createRuntimeArchive(runtimeDirectory, destination) {
  fs.rmSync(destination, { force: true })
  const result = spawnSync('zip', ['-qry', destination, '.'], {
    cwd: runtimeDirectory,
    stdio: 'inherit',
  })
  if (result.error) throw result.error
  if (result.status !== 0) {
    throw new Error(`Runtime 压缩失败，退出码 ${result.status}`)
  }
  return destination
}

export function stageShell(projectDirectory, runtimeDirectory, destination) {
  fs.rmSync(destination, { recursive: true, force: true })
  fs.mkdirSync(destination, { recursive: true })
  const packageJson = JSON.parse(
    fs.readFileSync(path.join(projectDirectory, 'package.json'), 'utf8'),
  )
  fs.writeFileSync(path.join(destination, 'package.json'), `${JSON.stringify({
    name: packageJson.name,
    version: packageJson.version,
    description: packageJson.description,
    main: 'bootstrap.js',
    private: true,
  }, null, 2)}\n`, 'utf8')
  for (const filename of [
    'bootstrap.js',
    'runtime-updater.js',
    'shell-info.json',
    'runtime-public-key.pem',
  ]) {
    fs.copyFileSync(
      path.join(projectDirectory, filename),
      path.join(destination, filename),
    )
  }
  fs.cpSync(runtimeDirectory, path.join(destination, 'runtime'), { recursive: true })
  return destination
}
