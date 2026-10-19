const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('regentDesktop', {
  currentServer: () => ipcRenderer.invoke('server:current'),
  connectServer: (value) => ipcRenderer.invoke('server:connect', value),
  localServer: () => ipcRenderer.invoke('server:use-local'),
})
