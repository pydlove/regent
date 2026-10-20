const { app, BrowserWindow } = require('electron')
const path = require('node:path')

app.whenReady().then(async () => {
  const window = new BrowserWindow({
    width: 440,
    height: 280,
    useContentSize: true,
    show: false,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })
  await window.loadFile(path.join(__dirname, '../shell/update.html'))
  const metrics = await window.webContents.executeJavaScript(`({
    innerWidth,
    innerHeight,
    scrollWidth: document.documentElement.scrollWidth,
    scrollHeight: document.documentElement.scrollHeight,
    bodyScrollHeight: document.body.scrollHeight,
    mainHeight: document.querySelector('main').getBoundingClientRect().height
  })`)
  console.log(JSON.stringify(metrics))
  window.destroy()
  app.quit()
})
