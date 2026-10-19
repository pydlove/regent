const status = document.querySelector('#status')
const progress = document.querySelector('#progress')
const progressBar = document.querySelector('.progress')

function render(value) {
  if (!value) return
  status.textContent = value.message
  const percent = Math.max(0, Math.min(100, Number(value.progress) || 0))
  progress.style.width = `${percent}%`
  progressBar.setAttribute('aria-valuenow', String(percent))
}

window.regentUpdate.current().then(render)
window.regentUpdate.onStatus(render)
