const { app, BrowserWindow, Menu } = require('electron')
const path = require('node:path')

function createWindow(width, height) {
  const window = new BrowserWindow({
    width,
    height,
    useContentSize: true,
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })
  window.setMenu(null)
  window.setMenuBarVisibility(false)
  return window
}

async function inspect(window, filename, query = undefined) {
  await window.loadFile(path.join(__dirname, '../shell', filename), { query })
  const metrics = await window.webContents.executeJavaScript(`({
    innerWidth,
    innerHeight,
    scrollWidth: document.documentElement.scrollWidth,
    scrollHeight: document.documentElement.scrollHeight,
    bodyScrollHeight: document.body.scrollHeight,
    mainHeight: document.querySelector('main').getBoundingClientRect().height,
    textAlign: getComputedStyle(document.querySelector('main')).textAlign,
    buttons: Array.from(document.querySelectorAll('button'), (button) => button.textContent.trim()),
    overflowing: document.documentElement.scrollHeight > document.documentElement.clientHeight
      || document.documentElement.scrollWidth > document.documentElement.clientWidth
  })`)
  metrics.menuBarVisible = window.isMenuBarVisible()
  return metrics
}

app.whenReady().then(async () => {
  Menu.setApplicationMenu(Menu.buildFromTemplate([
    { label: '文件', submenu: [{ role: 'quit' }] },
    { label: '编辑', submenu: [{ role: 'undo' }] },
    { label: '视图', submenu: [{ role: 'reload' }] },
    { label: '窗口', submenu: [{ role: 'minimize' }] },
  ]))
  const progressWindow = createWindow(440, 280)
  const promptWindow = createWindow(460, 360)
  const progress = await inspect(progressWindow, 'update.html')
  const prompt = await inspect(promptWindow, 'update-prompt.html', {
    version: 'Regent_V1.0.3_R100B002',
    notes: '修复 Runtime 更新死循环，并将更新提示文字改为左对齐。'.repeat(4),
    incremental: '1',
    size: '27 KB',
  })
  progressWindow.destroy()
  promptWindow.destroy()
  const result = {
    progress,
    prompt,
  }
  console.log(JSON.stringify(result, null, 2))
  if (progress.overflowing
      || prompt.overflowing
      || prompt.textAlign !== 'left'
      || JSON.stringify(prompt.buttons) !== JSON.stringify([
        '跳过此版本',
        '退出应用后升级',
        '现在升级',
      ])
      || (process.platform !== 'darwin' && (
        progress.menuBarVisible || prompt.menuBarVisible
      ))) {
    process.exitCode = 1
  }
  app.quit()
})
