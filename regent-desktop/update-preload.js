const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('regentUpdate', {
  current: () => ipcRenderer.invoke('update:current'),
  choose: (action) => ipcRenderer.send('update:prompt-choice', action),
  onStatus: (callback) => {
    const listener = (_event, status) => callback(status)
    ipcRenderer.on('update:status', listener)
    return () => ipcRenderer.removeListener('update:status', listener)
  },
})
