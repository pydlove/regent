import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  createRuntimeArchive,
  stageRuntime,
  stageShell,
} from './runtime-package.mjs'

const projectDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const packageJson = JSON.parse(fs.readFileSync(path.join(projectDirectory, 'package.json'), 'utf8'))
const shellInfo = JSON.parse(fs.readFileSync(path.join(projectDirectory, 'shell-info.json'), 'utf8'))
const version = packageJson.version
const releaseCode = process.env.REGENT_RELEASE_CODE?.trim() || shellInfo.shellRelease
const artifactVersion = releaseCode ? `${version}-${releaseCode}` : version
const releaseDirectory = path.join(projectDirectory, 'dist')
const workDirectory = path.join(releaseDirectory, 'work')
const assetsDirectory = path.join(projectDirectory, 'assets')
const electronPackager = path.join(
  projectDirectory,
  'node_modules',
  '.bin',
  'electron-packager',
)
const electronVersion = JSON.parse(
  fs.readFileSync(path.join(projectDirectory, 'node_modules', 'electron', 'package.json'), 'utf8'),
).version

function run(command, args, options = {}) {
  console.log(`\n$ ${command} ${args.join(' ')}`)
  const result = spawnSync(command, args, {
    cwd: options.cwd || projectDirectory,
    stdio: 'inherit',
    env: { ...process.env, ...options.env },
  })
  if (result.status !== 0) {
    throw new Error(`${command} 执行失败，退出码 ${result.status}`)
  }
}

function runWithRetry(command, args, options = {}) {
  const attempts = options.attempts || 3
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      run(command, args, options)
      return
    } catch (error) {
      if (attempt === attempts) throw error
      console.warn(`\n打包命令失败，${attempt}/${attempts}，3 秒后重试：${error.message}`)
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 3000)
    }
  }
}

function findCachedElectronZip(cacheRoot, filename) {
  if (!fs.existsSync(cacheRoot)) return null
  const pending = [cacheRoot]
  while (pending.length > 0) {
    const directory = pending.pop()
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const fullPath = path.join(directory, entry.name)
      if (entry.isDirectory()) {
        pending.push(fullPath)
      } else if (entry.isFile() && entry.name === filename) {
        return fullPath
      }
    }
  }
  return null
}

function prepareElectronZipDirectory() {
  const cacheRoots = [
    process.env.ELECTRON_CACHE,
    path.join(os.homedir(), 'Library', 'Caches', 'electron'),
    path.join(os.homedir(), '.cache', 'electron'),
  ].filter(Boolean)
  const filenames = [
    `electron-v${electronVersion}-darwin-x64.zip`,
    `electron-v${electronVersion}-darwin-arm64.zip`,
    `electron-v${electronVersion}-win32-x64.zip`,
  ]
  const sources = []
  for (const filename of filenames) {
    let source = null
    for (const root of cacheRoots) {
      source = findCachedElectronZip(root, filename)
      if (source) break
    }
    if (!source) return null
    sources.push([filename, source])
  }

  const zipDirectory = path.join(workDirectory, 'electron-zips')
  fs.rmSync(zipDirectory, { recursive: true, force: true })
  fs.mkdirSync(zipDirectory, { recursive: true })
  for (const [filename, source] of sources) {
    const target = path.join(zipDirectory, filename)
    try {
      fs.symlinkSync(source, target)
    } catch {
      fs.copyFileSync(source, target)
    }
  }
  console.log(`\n使用本地 Electron ZIP：${zipDirectory}`)
  return zipDirectory
}

function iconSet() {
  const iconset = path.join(workDirectory, 'Regent.iconset')
  fs.mkdirSync(iconset, { recursive: true })
  const sizes = [
    [16, 'icon_16x16.png'],
    [32, 'icon_16x16@2x.png'],
    [32, 'icon_32x32.png'],
    [64, 'icon_32x32@2x.png'],
    [128, 'icon_128x128.png'],
    [256, 'icon_128x128@2x.png'],
    [256, 'icon_256x256.png'],
    [512, 'icon_256x256@2x.png'],
    [512, 'icon_512x512.png'],
    [1024, 'icon_512x512@2x.png'],
  ]
  for (const [size, filename] of sizes) {
    run('magick', [
      '-background',
      'none',
      path.join(assetsDirectory, 'icon.svg'),
      '-resize',
      `${size}x${size}`,
      path.join(iconset, filename),
    ])
  }
  run('iconutil', ['-c', 'icns', iconset, '-o', path.join(assetsDirectory, 'icon.icns')])
  run('magick', [
    '-background',
    'none',
    path.join(assetsDirectory, 'icon.svg'),
    '-define',
    'icon:auto-resize=256,128,64,48,32,16',
    path.join(assetsDirectory, 'icon.ico'),
  ])
}

function packageApplication(sourceDirectory, platform, arch, outputDirectory, electronZipDir) {
  if (!fs.existsSync(electronPackager)) {
    throw new Error(`找不到本地 electron-packager：${electronPackager}`)
  }
  runWithRetry(electronPackager, [
    sourceDirectory,
    'Regent',
    `--platform=${platform}`,
    `--arch=${arch}`,
    `--out=${outputDirectory}`,
    '--overwrite',
    '--asar',
    '--app-bundle-id=com.pydlove.regent',
    `--app-version=${version}`,
    `--build-version=${version}`,
    ...(electronZipDir ? [`--electron-zip-dir=${electronZipDir}`] : []),
    ...(platform === 'darwin' && arch === 'universal' ? ['--osx-universal.mergeASARs=true'] : []),
    `--icon=${path.join(assetsDirectory, platform === 'darwin' ? 'icon.icns' : 'icon.ico')}`,
    '--ignore=^/dist',
    '--ignore=^/scripts',
    '--ignore=^/README\\.md$',
  ], {
    attempts: 3,
    env: {
      ELECTRON_MIRROR: process.env.ELECTRON_MIRROR || 'https://npmmirror.com/mirrors/electron/',
    },
  })
}

function buildMacDmg(electronZipDir, shellDirectory) {
  const macOutput = path.join(workDirectory, 'mac')
  packageApplication(shellDirectory, 'darwin', 'universal', macOutput, electronZipDir)
  const application = path.join(macOutput, 'Regent-darwin-universal', 'Regent.app')
  run('codesign', ['--force', '--deep', '--sign', '-', application])

  const stage = path.join(workDirectory, 'dmg-stage')
  fs.rmSync(stage, { recursive: true, force: true })
  fs.mkdirSync(stage, { recursive: true })
  const stagedApplication = path.join(stage, 'Regent.app')
  run('ditto', [application, stagedApplication])
  run('codesign', ['--force', '--deep', '--sign', '-', stagedApplication])
  fs.symlinkSync('/Applications', path.join(stage, 'Applications'))
  const installGuide = fs.readFileSync(
    path.join(assetsDirectory, '安装说明.txt'),
    'utf8',
  ).replaceAll('{{VERSION}}', artifactVersion)
  fs.writeFileSync(path.join(stage, '安装说明.txt'), installGuide, 'utf8')

  const dmg = path.join(releaseDirectory, `Regent-${artifactVersion}-macOS.dmg`)
  fs.rmSync(dmg, { force: true })
  run('hdiutil', [
    'create',
    '-volname',
    `Regent ${artifactVersion}`,
    '-srcfolder',
    stage,
    '-ov',
    '-format',
    'UDZO',
    dmg,
  ])
  return dmg
}

function buildWindowsZip(electronZipDir, shellDirectory) {
  const windowsOutput = path.join(workDirectory, 'windows')
  packageApplication(shellDirectory, 'win32', 'x64', windowsOutput, electronZipDir)
  const applicationDirectory = path.join(windowsOutput, 'Regent-win32-x64')
  fs.writeFileSync(
    path.join(applicationDirectory, 'Start-Device-Agent.cmd'),
    '@echo off\r\nstart "" /min "%~dp0Regent.exe" --device-agent\r\n',
    'utf8',
  )
  fs.writeFileSync(
    path.join(applicationDirectory, 'install-device-agent.ps1'),
    `$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$exe = Join-Path $root 'Regent.exe'
if (-not (Test-Path $exe)) { throw '缺少 Regent.exe' }
$shortcutPath = Join-Path ([Environment]::GetFolderPath('Startup')) 'Regent Device Agent.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $exe
$shortcut.Arguments = '--device-agent'
$shortcut.WorkingDirectory = $root
$shortcut.WindowStyle = 7
$shortcut.Save()
Start-Process -FilePath $exe -ArgumentList '--device-agent' -WorkingDirectory $root -WindowStyle Hidden
Write-Host 'Regent 设备代理已启动，并设置为登录后自动启动。'
`,
    'utf8',
  )
  fs.writeFileSync(
    path.join(applicationDirectory, 'uninstall-device-agent.ps1'),
    `$ErrorActionPreference = 'Stop'
$shortcutPath = Join-Path ([Environment]::GetFolderPath('Startup')) 'Regent Device Agent.lnk'
Remove-Item $shortcutPath -Force -ErrorAction SilentlyContinue
Get-CimInstance Win32_Process -Filter "Name='Regent.exe'" |
  Where-Object { $_.CommandLine -match '--device-agent' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Write-Host 'Regent 设备代理开机启动已移除。'
`,
    'utf8',
  )
  fs.writeFileSync(
    path.join(applicationDirectory, 'Install-Device-Agent-Autostart.cmd'),
    '@echo off\r\nchcp 65001 >nul\r\npowershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-device-agent.ps1"\r\npause\r\n',
    'utf8',
  )
  fs.writeFileSync(
    path.join(applicationDirectory, 'Uninstall-Device-Agent-Autostart.cmd'),
    '@echo off\r\nchcp 65001 >nul\r\npowershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0uninstall-device-agent.ps1"\r\npause\r\n',
    'utf8',
  )
  fs.writeFileSync(
    path.join(applicationDirectory, 'README-Windows-Agent.txt'),
    `Regent Windows 设备代理

首次使用：
1. 解压整个目录，不要只复制 Regent.exe。
2. 双击 Install-Device-Agent-Autostart.cmd。
3. 回到 Regent 网页并点击“重新检测”。

只启动一次：双击 Start-Device-Agent.cmd。
取消登录自启动：双击 Uninstall-Device-Agent-Autostart.cmd。
`,
    'utf8',
  )
  const zip = path.join(releaseDirectory, `Regent-${artifactVersion}-Windows-x64.zip`)
  fs.rmSync(zip, { force: true })
  run('ditto', [
    '-c',
    '-k',
    '--norsrc',
    '--noextattr',
    '--keepParent',
    applicationDirectory,
    zip,
  ])
  return zip
}

fs.rmSync(releaseDirectory, { recursive: true, force: true })
fs.mkdirSync(workDirectory, { recursive: true })
iconSet()
const electronZipDir = prepareElectronZipDirectory()
const runtimeDirectory = stageRuntime(projectDirectory, path.join(workDirectory, 'runtime'), {
  releaseCode,
  minShellRelease: shellInfo.minRuntimeShellRelease || shellInfo.shellRelease,
})
const runtime = createRuntimeArchive(
  runtimeDirectory,
  path.join(releaseDirectory, `Regent-${artifactVersion}-runtime.zip`),
)
const shellDirectory = stageShell(
  projectDirectory,
  runtimeDirectory,
  path.join(workDirectory, 'shell'),
)
const macos = buildMacDmg(electronZipDir, shellDirectory)
const windows = buildWindowsZip(electronZipDir, shellDirectory)

console.log('\n发布产物：')
console.log(`macOS: ${macos}`)
console.log(`Windows: ${windows}`)
console.log(`Runtime: ${runtime}`)
console.log(`平台: ${os.platform()} ${os.arch()}`)
