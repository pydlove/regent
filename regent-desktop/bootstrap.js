const { app } = require('electron')
const fs = require('node:fs')
const path = require('node:path')
const shellInfo = require('./shell-info.json')
const { RuntimeManager } = require('./runtime-updater')

const STARTUP_TIMEOUT_MILLIS = 20_000
const packagedRuntime = path.join(__dirname, 'runtime')
const builtinDirectory = fs.existsSync(path.join(packagedRuntime, 'main.js'))
  ? packagedRuntime
  : __dirname

function readPublicKey() {
  try {
    return fs.readFileSync(path.join(__dirname, 'runtime-public-key.pem'), 'utf8')
  } catch {
    return null
  }
}

const manager = new RuntimeManager({
  userData: app.getPath('userData'),
  builtinDirectory,
  builtinRelease: shellInfo.shellRelease,
  publicKey: readPublicKey(),
})
const startup = manager.prepareStartup()
let healthy = false
let startupTimer = null

function markHealthy() {
  if (healthy) return
  healthy = true
  if (startupTimer) clearTimeout(startupTimer)
  startupTimer = null
  try {
    manager.markHealthy()
  } catch (error) {
    manager.log(`Failed to persist healthy state: ${error.message}`)
  }
}

function relaunchAfterRollback(reason) {
  if (healthy) return
  healthy = true
  if (startupTimer) clearTimeout(startupTimer)
  startupTimer = null
  manager.forceRollback(reason)
  setImmediate(() => {
    app.relaunch()
    app.exit(1)
  })
}

startupTimer = setTimeout(() => {
  relaunchAfterRollback('runtime-startup-timeout')
}, STARTUP_TIMEOUT_MILLIS)

global.regentRuntime = {
  managed: true,
  shellRelease: shellInfo.shellRelease,
  activeRelease: startup.releaseCode,
  failedRelease: startup.state.failed,
  activeDirectory: startup.directory,
  markHealthy,
  verifySignature: (content, signature) => manager.verifySignature(content, signature),
  installRuntimeUpdate: (zipPath, descriptor) => manager.install(zipPath, descriptor),
  relaunchForUpdate: () => {
    app.relaunch()
    app.exit(0)
  },
}

manager.log(`Starting shell ${shellInfo.shellRelease}, runtime ${startup.releaseCode}`)
try {
  require(path.join(startup.directory, 'main.js'))
} catch (error) {
  manager.log(`Runtime require failed: ${error.stack || error.message}`)
  relaunchAfterRollback(`runtime-load-failed:${error.message}`)
}
