const { contextBridge, ipcRenderer, webUtils } = require('electron')

contextBridge.exposeInMainWorld('regentDesktop', {
  filePath: (file) => {
    try {
      return webUtils.getPathForFile(file) || null
    } catch {
      return null
    }
  },
  currentServer: () => ipcRenderer.invoke('server:current'),
  connectServer: (value) => ipcRenderer.invoke('server:connect', value),
  localServer: () => ipcRenderer.invoke('server:use-local'),
  deviceAgentStatus: () => ipcRenderer.invoke('device:status'),
  runTaskOnDevice: (payload) => ipcRenderer.invoke('device:run-task', payload),
  deviceJob: (jobId) => ipcRenderer.invoke('device:job', jobId),
})
