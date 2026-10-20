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
const releaseInfo = require('./release-info.json')

const APP_TITLE = 'Regent AI'
const PARTITION = 'persist:regent-desktop'
const execFileAsync = promisify(execFile)
const configPath = () => path.join(app.getPath('userData'), 'server.json')

let mainWindow = null
let configWindow = null
let updateWindow = null
let serverUrl = null
let updateTimer = null
let promptedReleaseCode = null
let updating = false
let updateStatus = { message: '正在准备更新…', progress: 0 }
let deviceAgent = null
let devicePairTimer = null

function readServerUrl() {
  try {
    const value = JSON.parse(fs.readFileSync(configPath(), 'utf8')).serverUrl
    return normalizeServerUrl(value)
  } catch {
    return null
  }
}

function saveServerUrl(value) {
  const normalized = normalizeServerUrl(value)
  fs.mkdirSync(path.dirname(configPath()), { recursive: true })
  fs.writeFileSync(configPath(), `${JSON.stringify({ serverUrl: normalized }, null, 2)}\n`, 'utf8')
  serverUrl = normalized
  return normalized
}

function normalizeServerUrl(value) {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error('请输入 Regent 服务地址')
  }
  let url
  try {
    url = new URL(value.trim())
  } catch {
    throw new Error('服务地址格式不正确')
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('服务地址必须以 http:// 或 https:// 开头')
  }
  if (url.username || url.password) {
    throw new Error('服务地址不能包含用户名或密码')
  }
  return url.origin
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
      buttons: ['重新配置', '退出'],
      defaultId: 0,
      cancelId: 1,
    }).then(({ response }) => {
      if (response === 0) openConfig()
      else app.quit()
    })
  })
  mainWindow.webContents.once('did-finish-load', () => {
    void checkDesktopUpdate()
    void ensureDeviceAgentPairing()
    updateTimer = setInterval(() => void checkDesktopUpdate(), 30 * 60 * 1000)
  })
  mainWindow.loadURL(serverUrl)
}

async function deviceSessionCookieHeader() {
  const cookies = await session.fromPartition(PARTITION).cookies.get({ url: serverUrl })
  return cookies.map((cookie) => `${cookie.name}=${cookie.value}`).join('; ')
}

async function ensureDeviceAgentPairing() {
  if (!deviceAgent || !serverUrl || !mainWindow || mainWindow.isDestroyed()) return false
  if (await deviceAgent.validatePairing()) return true
  try {
    const cookie = await deviceSessionCookieHeader()
    if (!cookie) return false
    const response = await fetch(`${serverUrl}/api/auth/device-session`, {
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
    deviceAgent.configure(serverUrl, grant.token)
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
    const response = await fetch(`${serverUrl}/api/release/latest`, {
      cache: 'no-store',
      signal: AbortSignal.timeout(8000),
    })
    if (!response.ok) return
    const latest = await response.json()
    const releaseCode = typeof latest.releaseCode === 'string' ? latest.releaseCode : ''
    if (!releaseCode
        || releaseCode === releaseInfo.releaseCode
        || releaseCode === promptedReleaseCode) {
      return
    }
    promptedReleaseCode = releaseCode
    const { response: action } = await dialog.showMessageBox(mainWindow, {
      type: 'info',
      title: '发现新版本',
      message: `Regent ${latest.version || releaseCode} 已发布`,
      detail: `${latest.notes || '包含功能更新与问题修复。'}\n\n点击“下载并安装”后，客户端会自动下载、安装并重启。`,
      buttons: ['下载并安装', '稍后'],
      defaultId: 0,
      cancelId: 1,
    })
    if (action === 0) {
      await installUpdate(latest)
    }
  } catch {
    // 网络不可用时静默重试，不打断当前工作。
  }
}

async function installUpdate(latest) {
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
  const asset = latest.downloads?.[platform]
  if (!asset?.url || !asset?.sha256) {
    updating = false
    await showUpdateFailure('发布清单缺少安装包或 SHA-256')
    return
  }

  const workDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-update-'))
  try {
    createUpdateWindow()
    sendUpdateStatus('正在下载新版本…', 0)
    const extension = platform === 'macos' ? '.dmg' : '.zip'
    const downloadPath = path.join(workDirectory, `Regent-update${extension}`)
    const downloadUrl = new URL(asset.url, serverUrl).toString()
    await downloadUpdate(downloadUrl, downloadPath, asset.sizeBytes)

    sendUpdateStatus('正在校验安装包…', 92)
    const digest = await sha256(downloadPath)
    if (digest.toLowerCase() !== String(asset.sha256).toLowerCase()) {
      throw new Error('安装包校验失败，请稍后重试')
    }

    sendUpdateStatus('正在安装，客户端即将重启…', 97)
    if (platform === 'macos') {
      await installMacUpdate(downloadPath, workDirectory)
    } else {
      await installWindowsUpdate(downloadPath, workDirectory)
    }
  } catch (error) {
    updating = false
    closeUpdateWindow()
    await showUpdateFailure(error instanceof Error ? error.message : String(error))
    fs.rmSync(workDirectory, { recursive: true, force: true })
  }
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
    await shell.openExternal(`${serverUrl}/download`)
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
  updateWindow.once('ready-to-show', () => updateWindow?.show())
  updateWindow.on('closed', () => {
    updateWindow = null
  })
  updateWindow.loadFile(path.join(__dirname, 'shell', 'update.html'))
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

async function installMacUpdate(downloadPath, workDirectory) {
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
  app.quit()
}

async function installWindowsUpdate(downloadPath, workDirectory) {
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
  app.quit()
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
ipcMain.handle('device:status', async () => {
  if (!deviceAgent) throw new Error('设备代理尚未初始化')
  await ensureDeviceAgentPairing()
  return deviceAgent.status()
})
ipcMain.handle('device:run-task', async (_event, payload) => {
  if (!deviceAgent) throw new Error('设备代理尚未初始化')
  if (!(await ensureDeviceAgentPairing())) {
    throw new Error('当前设备代理尚未完成配对，请先登录并稍候重试')
  }
  return deviceAgent.runTask(payload || {})
})
ipcMain.handle('device:job', (_event, jobId) => {
  const job = deviceAgent?.jobs.get(String(jobId))
  if (!job) throw new Error('设备执行任务不存在')
  return job
})

app.whenReady().then(async () => {
  serverUrl = readServerUrl()
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
  buildMenu()
  await session.fromPartition(PARTITION).setPermissionRequestHandler(
    (webContents, permission, callback) => {
      const allowed = new Set(['media', 'clipboard-sanitized-write', 'notifications'])
      callback(allowed.has(permission) && isSameOrigin(webContents.getURL(), serverUrl))
    },
  )
  if (serverUrl) createMainWindow()
  else createConfigWindow()
  scheduleDevicePairing()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      if (serverUrl) createMainWindow()
      else createConfigWindow()
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
