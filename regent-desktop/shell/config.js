const form = document.querySelector('#server-form')
const input = document.querySelector('#server-url')
const error = document.querySelector('#error')
const connectButton = document.querySelector('#connect-button')
const connectLabel = document.querySelector('#connect-label')
const localButton = document.querySelector('#local-button')

const initial = new URLSearchParams(location.search).get('serverUrl')
input.value = initial || ''

function showError(message) {
  error.textContent = message
  error.hidden = false
}

function setBusy(busy) {
  connectButton.disabled = busy
  localButton.disabled = busy
  connectLabel.textContent = busy ? '正在连接…' : '连接并打开'
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  error.hidden = true
  setBusy(true)
  try {
    await window.regentDesktop.connectServer(input.value)
  } catch (reason) {
    showError(reason instanceof Error ? reason.message : '无法连接服务')
    setBusy(false)
  }
})

localButton.addEventListener('click', async () => {
  input.value = await window.regentDesktop.localServer()
  input.focus()
  input.select()
})
