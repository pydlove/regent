const { app, BrowserWindow, Menu, dialog, ipcMain, session, shell } = require('electron')
const { execFile, spawn } = require('node:child_process')
const crypto = require('node:crypto')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { Readable, Transform } = require('node:stream')
const { pipeline } = require('node:stream/promises')
const { URL } = require('node:url')
const { promisify } = require('node:util')
const { DeviceAgent } = require('./device-agent')
const {
  DEFAULT_LANDING_PATH,
  DEFAULT_SERVER_URL,
  appendServerPath,
  normalizeServerUrl,
} = require('./server-url')
const { isShellCompatible } = require('./runtime-updater')
const {
  clientUpdateReleaseCode,
  shouldPromptForClientUpdate,
} = require('./update-policy')
const {
  readSkippedRelease,
  writeSkippedRelease,
} = require('./update-preferences')
const releaseInfo = require('./release-info.json')

const APP_TITLE = 'Regent AI'
const PARTITION = 'persist:regent-desktop'
const LOCAL_DEVICE_AGENT_URL = 'http://127.0.0.1:12601'
const DEVICE_AGENT_MODE = process.argv.includes('--device-agent')
const execFileAsync = promisify(execFile)
const configPath = () => path.join(app.getPath('userData'), 'server.json')
const updatePreferencesPath = () => path.join(app.getPath('userData'), 'update-preferences.json')

let mainWindow = null
let configWindow = null
let updateWindow = null
let updatePromptChoice = null
let serverUrl = null
let usingBuiltInServer = false
let updateTimer = null
let promptedReleaseCode = null
let skippedReleaseCode = null
let updating = false
let updateStatus = { message: '正在准备更新…', progress: 0 }
let deviceAgent = null
let devicePairTimer = null
let externalDeviceAgentUrl = null

function readServerUrl() {
  try {
    const value = JSON.parse(fs.readFileSync(configPath(), 'utf8')).serverUrl
    const normalized = normalizeServerUrl(value)
    usingBuiltInServer = normalized === DEFAULT_SERVER_URL
    return normalized
  } catch {
    usingBuiltInServer = true
    return DEFAULT_SERVER_URL
  }
}

function saveServerUrl(value) {
  const normalized = normalizeServerUrl(value)
  fs.mkdirSync(path.dirname(configPath()), { recursive: true })
  fs.writeFileSync(configPath(), `${JSON.stringify({ serverUrl: normalized }, null, 2)}\n`, 'utf8')
  serverUrl = normalized
  usingBuiltInServer = normalized === DEFAULT_SERVER_URL
  return normalized
}

function mainWindowUrl() {
  return usingBuiltInServer
    ? appendServerPath(serverUrl, DEFAULT_LANDING_PATH)
    : serverUrl
}

async function probeServer(value) {
  const normalized = normalizeServerUrl(value)
  let response
  try {
    response = await fetch(normalized, {
      redirect: 'follow',
      signal: AbortSignal.timeout(8000),
    })
  } catch {
    throw new Error('无法连接服务，请确认地址、网络和 HTTPS 证书')
  }
  if (!response.ok) {
    throw new Error(`服务返回 HTTP ${response.status}`)
  }
  return normalized
}

function createConfigWindow() {
  if (configWindow) {
    configWindow.focus()
    return
  }
  configWindow = new BrowserWindow({
    width: 520,
    height: 570,
    minWidth: 480,
    minHeight: 520,
    title: `${APP_TITLE} · 连接服务`,
    backgroundColor: '#ececec',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })
  configWindow.once('ready-to-show', () => configWindow.show())
  configWindow.on('closed', () => {
    configWindow = null
  })
  configWindow.loadFile(path.join(__dirname, 'shell', 'config.html'), {
    query: { serverUrl: serverUrl || '' },
  })
}

function createMainWindow() {
  if (!serverUrl) {
    createConfigWindow()
    return
  }
  if (mainWindow) {
    mainWindow.focus()
    return
  }
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 980,
    minHeight: 680,
    title: APP_TITLE,
    backgroundColor: '#ececec',
    show: false,
    webPreferences: {
      partition: PARTITION,
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })
  mainWindow.once('ready-to-show', () => mainWindow.show())
  mainWindow.on('closed', () => {
    if (updateTimer) {
      clearInterval(updateTimer)
      updateTimer = null
    }
    mainWindow = null
  })
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (isSameOrigin(url, serverUrl)) {
      return { action: 'allow' }
    }
    void shell.openExternal(url)
    return { action: 'deny' }
  })
  mainWindow.webContents.on('will-navigate', (event, url) => {
    if (isSameOrigin(url, serverUrl)) return
    event.preventDefault()
    void shell.openExternal(url)
  })
  mainWindow.webContents.on('did-fail-load', (_event, code, description, validatedUrl) => {
    if (code === -3 || String(validatedUrl).startsWith('devtools://')) return
    void dialog.showMessageBox(mainWindow, {
      type: 'error',
      title: '无法打开 Regent',
      message: '连接 Regent 服务失败',
      detail: `${description}\n${serverUrl}`,
      buttons: ['重试', '退出'],
      defaultId: 0,
      cancelId: 1,
    }).then(({ response }) => {
      if (response === 0) {
        void mainWindow?.loadURL(mainWindowUrl())
      } else {
        app.quit()
      }
    })
  })
  mainWindow.webContents.once('did-finish-load', () => {
    void checkDesktopUpdate()
    void ensureDeviceAgentPairing()
    updateTimer = setInterval(() => void checkDesktopUpdate(), 30 * 60 * 1000)
  })
  mainWindow.loadURL(mainWindowUrl())
}

async function deviceSessionCookieHeader() {
  const cookies = await session.fromPartition(PARTITION).cookies.get({ url: serverUrl })
  return cookies.map((cookie) => `${cookie.name}=${cookie.value}`).join('; ')
}

async function ensureDeviceAgentPairing() {
  if (!serverUrl || !mainWindow || mainWindow.isDestroyed()) return false
  try {
    if (externalDeviceAgentUrl) {
      const current = await externalAgentRequest('/status')
      if (current.paired && current.serverUrl
          && normalizeServerUrl(current.serverUrl) === serverUrl) {
        return true
      }
    } else if (await deviceAgent?.validatePairing()) {
      return true
    }
    const cookie = await deviceSessionCookieHeader()
    if (!cookie) return false
    const response = await fetch(serverEndpoint('/api/auth/device-session'), {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        Cookie: cookie,
        'X-Regent-Relay-Request': '1',
      },
    })
    if (!response.ok) return false
    const grant = await response.json()
    if (!grant?.token) return false
    if (externalDeviceAgentUrl) {
      await externalAgentRequest('/pair', {
        method: 'POST',
        body: JSON.stringify({ serverUrl, token: grant.token }),
      })
    } else {
      deviceAgent.configure(serverUrl, grant.token)
    }
    return true
  } catch {
    return false
  }
}

function scheduleDevicePairing() {
  if (!mainWindow || mainWindow.isDestroyed()) return
  if (devicePairTimer) clearTimeout(devicePairTimer)
  devicePairTimer = setTimeout(async () => {
    devicePairTimer = null
    if (!(await ensureDeviceAgentPairing())) scheduleDevicePairing()
  }, 5000)
}

async function checkDesktopUpdate() {
  if (!serverUrl || !mainWindow || mainWindow.isDestroyed() || updating) return
  try {
    const response = await fetch(serverEndpoint('/api/release/latest'), {
      cache: 'no-store',
      signal: AbortSignal.timeout(8000),
    })
    if (!response.ok) return
    const latest = await response.json()
    const releaseCode = clientUpdateReleaseCode(latest)
    if (!releaseCode || !shouldPromptForClientUpdate(
      latest,
      global.regentRuntime?.activeRelease || releaseInfo.releaseCode,
      promptedReleaseCode,
      global.regentRuntime?.failedRelease,
      skippedReleaseCode,
    )) {
      return
    }
    promptedReleaseCode = releaseCode
    const incremental = canInstallRuntimeUpdate(latest.runtime)
    const action = await showUpdatePrompt(latest, incremental)
    if (action === 'skip') {
      try {
        skippedReleaseCode = writeSkippedRelease(updatePreferencesPath(), releaseCode)
      } catch (error) {
        console.warn(`保存跳过版本失败：${error.message}`)
      }
      return
    }
    if (action === 'install' || action === 'defer') {
      await installUpdate(latest, action === 'install')
    }
  } catch {
    // 网络不可用时静默重试，不打断当前工作。
  }
}

function canInstallRuntimeUpdate(runtime) {
  return Boolean(
    global.regentRuntime?.managed
    && global.regentRuntime.installRuntimeUpdate
    && runtime?.releaseCode
    && runtime?.url
    && runtime?.sha256
    && runtime?.signature
    && isShellCompatible(runtime.minShellRelease, global.regentRuntime.shellRelease),
  )
}

function settleUpdatePrompt(action) {
  const pending = updatePromptChoice
  if (!pending) return
  updatePromptChoice = null
  pending.resolve(action)
  if (pending.window && !pending.window.isDestroyed()) pending.window.destroy()
}

function showUpdatePrompt(latest, incremental) {
  const prompt = new BrowserWindow({
    width: 460,
    height: 360,
    useContentSize: true,
    resizable: false,
    minimizable: false,
    maximizable: false,
    autoHideMenuBar: true,
    title: '发现新版本',
    backgroundColor: '#ececec',
    parent: mainWindow || undefined,
    modal: Boolean(mainWindow),
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'update-preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })
  prompt.setMenu(null)
  prompt.setMenuBarVisibility(false)
  return new Promise((resolve) => {
    updatePromptChoice = { window: prompt, resolve }
    prompt.once('ready-to-show', () => {
      if (!prompt.isDestroyed()) prompt.show()
    })
    prompt.on('closed', () => {
      const pending = updatePromptChoice
      if (pending?.window === prompt) {
        updatePromptChoice = null
        pending.resolve('cancel')
      }
    })
    prompt.loadFile(path.join(__dirname, 'shell', 'update-prompt.html'), {
      query: {
        version: String(latest.version || latest.releaseCode || ''),
        notes: String(latest.notes || '包含功能更新与问题修复。'),
        incremental: incremental ? '1' : '0',
        size: incremental ? formatBytes(latest.runtime.sizeBytes) : '',
      },
    }).catch(() => settleUpdatePrompt('cancel'))
  })
}

async function installUpdate(latest, restartImmediately = true) {
  if (updating) return
  updating = true
  const platform = process.platform === 'darwin'
    ? 'macos'
    : process.platform === 'win32'
      ? 'windows'
      : null
  if (!platform) {
    updating = false
    await showUpdateFailure('当前系统暂不支持自动更新')
    return
  }
  const workDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-update-'))
  try {
    if (canInstallRuntimeUpdate(latest.runtime)) {
      await installRuntimeUpdate(latest.runtime, workDirectory, restartImmediately)
      return
    }
    const asset = latest.downloads?.[platform]
    if (!asset?.url || !asset?.sha256) {
      throw new Error('发布清单缺少可用的安装包或 SHA-256')
    }
    createUpdateWindow()
    sendUpdateStatus('正在下载新版本…', 0)
    const extension = platform === 'macos' ? '.dmg' : '.zip'
    const downloadPath = path.join(workDirectory, `Regent-update${extension}`)
    const downloadUrl = serverEndpoint(asset.url)
    await downloadUpdate(downloadUrl, downloadPath, asset.sizeBytes)

    sendUpdateStatus('正在校验安装包…', 92)
    const digest = await sha256(downloadPath)
    if (digest.toLowerCase() !== String(asset.sha256).toLowerCase()) {
      throw new Error('安装包校验失败，请稍后重试')
    }

    sendUpdateStatus(
      restartImmediately
        ? '正在安装，客户端即将重启…'
        : '正在准备升级包，完成后可继续使用…',
      97,
    )
    if (platform === 'macos') {
      await installMacUpdate(downloadPath, workDirectory, restartImmediately)
    } else {
      await installWindowsUpdate(downloadPath, workDirectory, restartImmediately)
    }
    if (!restartImmediately) {
      finishDeferredUpdate()
    }
  } catch (error) {
    updating = false
    closeUpdateWindow()
    await showUpdateFailure(error instanceof Error ? error.message : String(error))
    fs.rmSync(workDirectory, { recursive: true, force: true })
  }
}

async function installRuntimeUpdate(runtime, workDirectory, restartImmediately) {
  createUpdateWindow()
  sendUpdateStatus('正在下载增量更新…', 0)
  const downloadPath = path.join(workDirectory, 'Regent-runtime.zip')
  await downloadUpdate(serverEndpoint(runtime.url), downloadPath, runtime.sizeBytes)

  sendUpdateStatus('正在校验增量更新…', 92)
  const digest = await sha256(downloadPath)
  if (digest.toLowerCase() !== String(runtime.sha256).toLowerCase()) {
    throw new Error('增量更新校验失败，请稍后重试')
  }

  sendUpdateStatus('正在安装增量更新…', 96)
  global.regentRuntime.installRuntimeUpdate(downloadPath, runtime)
  fs.rmSync(workDirectory, { recursive: true, force: true })
  if (restartImmediately) {
    sendUpdateStatus('更新完成，正在重启…', 100)
    setTimeout(() => global.regentRuntime.relaunchForUpdate(), 500)
  } else {
    finishDeferredUpdate()
  }
}

function finishDeferredUpdate() {
  updating = false
  sendUpdateStatus('升级包已准备，退出应用后自动安装…', 100)
  setTimeout(() => {
    if (!updating) closeUpdateWindow()
  }, 1200)
}

async function showUpdateFailure(detail) {
  const { response } = await dialog.showMessageBox(mainWindow, {
    type: 'error',
    title: '自动更新失败',
    message: '未能完成新版本安装',
    detail,
    buttons: ['打开下载页', '完成'],
    defaultId: 0,
    cancelId: 1,
  })
  if (response === 0) {
    await shell.openExternal(serverEndpoint('/download'))
  }
}

function createUpdateWindow() {
  if (updateWindow) return
  updateWindow = new BrowserWindow({
    width: 440,
    height: 280,
    // 更新卡片内容高度固定，按内容区计算避免标题栏挤占后出现窗口滚动条。
    useContentSize: true,
    resizable: false,
    closable: false,
    minimizable: false,
    maximizable: false,
    autoHideMenuBar: true,
    title: '更新 Regent',
    backgroundColor: '#ececec',
    parent: mainWindow || undefined,
    modal: Boolean(mainWindow),
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'update-preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })
  updateWindow.setMenu(null)
  updateWindow.setMenuBarVisibility(false)
  updateWindow.once('ready-to-show', async () => {
    const target = updateWindow
    if (!target || target.isDestroyed()) return
    await fitUpdateWindowToContent(target).catch(() => undefined)
    if (!target.isDestroyed()) target.show()
  })
  updateWindow.on('closed', () => {
    updateWindow = null
  })
  updateWindow.loadFile(path.join(__dirname, 'shell', 'update.html'))
}

async function fitUpdateWindowToContent(target) {
  const metrics = await target.webContents.executeJavaScript(`({
    width: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
    height: Math.max(document.documentElement.scrollHeight, document.body.scrollHeight),
    clientWidth: document.documentElement.clientWidth,
    clientHeight: document.documentElement.clientHeight
  })`)
  const [width, height] = target.getContentSize()
  const nextWidth = Math.min(520, Math.max(width, Math.ceil(metrics.width || 0)))
  const nextHeight = Math.min(360, Math.max(height, Math.ceil(metrics.height || 0)))
  if (nextWidth > metrics.clientWidth || nextHeight > metrics.clientHeight) {
    target.setContentSize(nextWidth, nextHeight)
  }
}

function sendUpdateStatus(message, progress) {
  updateStatus = { message, progress }
  if (!updateWindow || updateWindow.isDestroyed()) return
  updateWindow.webContents.send('update:status', { message, progress })
}

function closeUpdateWindow() {
  if (updateWindow && !updateWindow.isDestroyed()) updateWindow.destroy()
  updateWindow = null
}

async function downloadUpdate(url, destination, expectedSize) {
  const response = await fetch(url, {
    cache: 'no-store',
    signal: AbortSignal.timeout(30 * 60 * 1000),
  })
  if (!response.ok || !response.body) {
    throw new Error(`下载安装包失败（HTTP ${response.status}）`)
  }
  const total = Number(response.headers.get('content-length')) || Number(expectedSize) || 0
  let downloaded = 0
  const progress = new Transform({
    transform(chunk, _encoding, callback) {
      downloaded += chunk.length
      const percent = total > 0 ? Math.min(90, Math.round((downloaded / total) * 90)) : 0
      sendUpdateStatus(
        total > 0
          ? `正在下载新版本… ${formatBytes(downloaded)} / ${formatBytes(total)}`
          : '正在下载新版本…',
        percent,
      )
      callback(null, chunk)
    },
  })
  await pipeline(
    Readable.fromWeb(response.body),
    progress,
    fs.createWriteStream(destination),
  )
}

async function sha256(file) {
  const hash = crypto.createHash('sha256')
  await pipeline(fs.createReadStream(file), new Transform({
    transform(chunk, _encoding, callback) {
      hash.update(chunk)
      callback(null, chunk)
    },
  }), new Transform({
    transform(_chunk, _encoding, callback) {
      callback()
    },
  }))
  return hash.digest('hex')
}

async function installMacUpdate(downloadPath, workDirectory, restartImmediately) {
  const mountDirectory = path.join(workDirectory, 'mount')
  const stagedApplication = path.join(workDirectory, 'Regent.app')
  fs.mkdirSync(mountDirectory, { recursive: true })
  await execFileAsync('hdiutil', [
    'attach',
    '-nobrowse',
    '-readonly',
    '-mountpoint',
    mountDirectory,
    downloadPath,
  ])
  try {
    const sourceApplication = path.join(mountDirectory, 'Regent.app')
    if (!fs.existsSync(sourceApplication)) {
      throw new Error('安装包中没有找到 Regent.app')
    }
    await execFileAsync('ditto', [sourceApplication, stagedApplication])
  } finally {
    await execFileAsync('hdiutil', ['detach', mountDirectory]).catch(() => undefined)
  }
  await execFileAsync('xattr', ['-cr', stagedApplication]).catch(() => undefined)
  await execFileAsync('codesign', ['--verify', '--deep', '--strict', stagedApplication])

  const targetApplication = path.resolve(path.dirname(app.getPath('exe')), '../..')
  if (!targetApplication.endsWith('.app')) {
    throw new Error(`无法识别应用安装位置：${targetApplication}`)
  }
  fs.accessSync(path.dirname(targetApplication), fs.constants.W_OK)
  const scriptPath = path.join(workDirectory, 'install-update.sh')
  const backupPath = `${targetApplication}.old-${Date.now()}`
  fs.writeFileSync(scriptPath, `#!/bin/bash
set -e
while kill -0 ${process.pid} 2>/dev/null; do sleep 0.5; done
mv ${quote(targetApplication)} ${quote(backupPath)}
if ! ditto ${quote(stagedApplication)} ${quote(targetApplication)}; then
  mv ${quote(backupPath)} ${quote(targetApplication)}
  exit 1
fi
xattr -cr ${quote(targetApplication)} 2>/dev/null || true
open ${quote(targetApplication)}
rm -rf ${quote(workDirectory)}
`, { mode: 0o755 })
  spawn('/bin/bash', [scriptPath], {
    detached: true,
    stdio: 'ignore',
  }).unref()
  if (restartImmediately) app.quit()
}

async function installWindowsUpdate(downloadPath, workDirectory, restartImmediately) {
  const extractDirectory = path.join(workDirectory, 'extracted')
  await execFileAsync('powershell.exe', [
    '-NoProfile',
    '-NonInteractive',
    '-Command',
    `Expand-Archive -LiteralPath ${quotePowerShell(downloadPath)} -DestinationPath ${quotePowerShell(extractDirectory)} -Force`,
  ])
  const executable = findFile(extractDirectory, 'Regent.exe')
  if (!executable) throw new Error('安装包中没有找到 Regent.exe')
  const sourceDirectory = path.dirname(executable)
  const targetDirectory = path.dirname(app.getPath('exe'))
  const backupDirectory = `${targetDirectory}.old-${Date.now()}`
  const scriptPath = path.join(workDirectory, 'install-update.ps1')
  fs.writeFileSync(scriptPath, `
$ErrorActionPreference = 'Stop'
Wait-Process -Id ${process.pid} -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 800
Move-Item -LiteralPath ${quotePowerShell(targetDirectory)} -Destination ${quotePowerShell(backupDirectory)}
try {
  New-Item -ItemType Directory -Path ${quotePowerShell(targetDirectory)} | Out-Null
  Copy-Item -Path (Join-Path ${quotePowerShell(sourceDirectory)} '*') -Destination ${quotePowerShell(targetDirectory)} -Recurse -Force
} catch {
  Remove-Item -LiteralPath ${quotePowerShell(targetDirectory)} -Recurse -Force -ErrorAction SilentlyContinue
  Move-Item -LiteralPath ${quotePowerShell(backupDirectory)} -Destination ${quotePowerShell(targetDirectory)}
  throw
}
Start-Process -FilePath (Join-Path ${quotePowerShell(targetDirectory)} 'Regent.exe')
Remove-Item -LiteralPath ${quotePowerShell(backupDirectory)} -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath ${quotePowerShell(workDirectory)} -Recurse -Force
`, 'utf8')
  spawn('powershell.exe', [
    '-NoProfile',
    '-ExecutionPolicy',
    'Bypass',
    '-File',
    scriptPath,
  ], {
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
  }).unref()
  if (restartImmediately) app.quit()
}

function findFile(directory, filename) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const candidate = path.join(directory, entry.name)
    if (entry.isFile() && entry.name === filename) return candidate
    if (entry.isDirectory()) {
      const nested = findFile(candidate, filename)
      if (nested) return nested
    }
  }
  return null
}

function quote(value) {
  return `'${String(value).replaceAll("'", "'\\''")}'`
}

function quotePowerShell(value) {
  return `'${String(value).replaceAll("'", "''")}'`
}

function formatBytes(bytes) {
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function isSameOrigin(value, base) {
  try {
    return new URL(value).origin === new URL(base).origin
  } catch {
    return false
  }
}

function serverEndpoint(pathname) {
  return appendServerPath(serverUrl, pathname)
}

async function probeExternalDeviceAgent() {
  try {
    const response = await fetch(`${LOCAL_DEVICE_AGENT_URL}/status`, {
      signal: AbortSignal.timeout(3000),
    })
    return response.ok ? LOCAL_DEVICE_AGENT_URL : null
  } catch {
    return null
  }
}

async function externalAgentRequest(pathname, init = {}) {
  const response = await fetch(`${externalDeviceAgentUrl}${pathname}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...(init.headers || {}),
    },
    signal: AbortSignal.timeout(15000),
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    const message = typeof body.error === 'string'
      ? body.error
      : `设备代理请求失败（HTTP ${response.status}）`
    throw new Error(message)
  }
  return body
}

function openConfig() {
  if (updateTimer) {
    clearInterval(updateTimer)
    updateTimer = null
  }
  if (mainWindow) {
    mainWindow.destroy()
    mainWindow = null
  }
  createConfigWindow()
}

function buildMenu() {
  const template = [
    ...(process.platform === 'darwin'
      ? [{
          label: APP_TITLE,
          submenu: [
            { role: 'about', label: `关于 ${APP_TITLE}` },
            { type: 'separator' },
            { role: 'hide', label: '隐藏' },
            { role: 'hideOthers', label: '隐藏其他' },
            { role: 'unhide', label: '全部显示' },
            { type: 'separator' },
            { role: 'quit', label: '退出 Regent AI' },
          ],
        }]
      : []),
    {
      label: '文件',
      submenu: [
        { label: '切换服务地址…', click: openConfig },
        { type: 'separator' },
        process.platform === 'darwin'
          ? { role: 'close', label: '关闭窗口' }
          : { role: 'quit', label: '退出' },
      ],
    },
    {
      label: '编辑',
      submenu: [
        { role: 'undo', label: '撤销' },
        { role: 'redo', label: '重做' },
        { type: 'separator' },
        { role: 'cut', label: '剪切' },
        { role: 'copy', label: '复制' },
        { role: 'paste', label: '粘贴' },
        { role: 'selectAll', label: '全选' },
      ],
    },
    {
      label: '视图',
      submenu: [
        { role: 'reload', label: '重新加载' },
        { role: 'toggleDevTools', label: '开发者工具' },
        { type: 'separator' },
        { role: 'resetZoom', label: '实际大小' },
        { role: 'zoomIn', label: '放大' },
        { role: 'zoomOut', label: '缩小' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: '全屏' },
      ],
    },
    {
      label: '窗口',
      submenu: [
        { role: 'minimize', label: '最小化' },
        { role: 'zoom', label: '缩放' },
      ],
    },
  ]
  Menu.setApplicationMenu(Menu.buildFromTemplate(template))
}

ipcMain.handle('server:current', () => serverUrl)
ipcMain.handle('server:connect', async (_event, value) => {
  const normalized = await probeServer(value)
  saveServerUrl(normalized)
  if (deviceAgent) {
    deviceAgent.serverUrl = normalized
    deviceAgent.token = null
    deviceAgent.saveState()
  }
  if (configWindow) {
    configWindow.close()
    configWindow = null
  }
  createMainWindow()
  scheduleDevicePairing()
  return normalized
})
ipcMain.handle('server:use-local', () => 'http://127.0.0.1:12580')
ipcMain.handle('update:current', () => updateStatus)
ipcMain.on('update:prompt-choice', (_event, action) => {
  const choice = action === 'install' || action === 'defer' || action === 'skip'
    ? action
    : 'cancel'
  settleUpdatePrompt(choice)
})
ipcMain.handle('device:status', async () => {
  await ensureDeviceAgentPairing()
  if (externalDeviceAgentUrl) return externalAgentRequest('/status')
  if (!deviceAgent) throw new Error('设备代理尚未初始化')
  return deviceAgent.status()
})
ipcMain.handle('device:run-task', async (_event, payload) => {
  if (!(await ensureDeviceAgentPairing())) {
    throw new Error('当前设备代理尚未完成配对，请先登录并稍候重试')
  }
  if (externalDeviceAgentUrl) {
    return externalAgentRequest('/run-task', {
      method: 'POST',
      body: JSON.stringify(payload || {}),
    })
  }
  if (!deviceAgent) throw new Error('设备代理尚未初始化')
  return deviceAgent.runTask(payload || {})
})
ipcMain.handle('device:job', async (_event, jobId) => {
  if (externalDeviceAgentUrl) {
    return externalAgentRequest(`/jobs/${encodeURIComponent(String(jobId))}`)
  }
  const job = deviceAgent?.jobs.get(String(jobId))
  if (!job) throw new Error('设备执行任务不存在')
  return job
})

app.whenReady().then(async () => {
  if (DEVICE_AGENT_MODE) {
    app.dock?.hide()
    deviceAgent = new DeviceAgent({
      stateFile: path.join(app.getPath('userData'), 'device-agent.json'),
    })
    deviceAgent.loadState()
    await deviceAgent.startLocalServer()
    global.regentRuntime?.markHealthy?.()
    return
  }
  serverUrl = readServerUrl()
  skippedReleaseCode = readSkippedRelease(updatePreferencesPath())
  externalDeviceAgentUrl = await probeExternalDeviceAgent()
  if (!externalDeviceAgentUrl) {
    deviceAgent = new DeviceAgent({
      stateFile: path.join(app.getPath('userData'), 'device-agent.json'),
    })
    deviceAgent.loadState()
    if (deviceAgent.serverUrl !== serverUrl) {
      deviceAgent.serverUrl = serverUrl
      deviceAgent.token = null
      deviceAgent.saveState()
    }
    await deviceAgent.startLocalServer().catch((error) => {
      console.warn(`设备代理本地服务启动失败：${error.message}`)
    })
  }
  buildMenu()
  await session.fromPartition(PARTITION).setPermissionRequestHandler(
    (webContents, permission, callback) => {
      const allowed = new Set(['media', 'clipboard-sanitized-write', 'notifications'])
      callback(allowed.has(permission) && isSameOrigin(webContents.getURL(), serverUrl))
    },
  )
  createMainWindow()
  scheduleDevicePairing()
  global.regentRuntime?.markHealthy?.()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createMainWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})

app.on('before-quit', () => {
  if (devicePairTimer) clearTimeout(devicePairTimer)
  deviceAgent?.stopLocalServer()
})
