const params = new URLSearchParams(window.location.search)
const version = params.get('version') || 'Regent 新版本'
const notes = params.get('notes') || '包含功能更新与问题修复。'
const incremental = params.get('incremental') === '1'
const size = params.get('size') || ''

document.querySelector('#version').textContent = `${version} 已发布`
document.querySelector('#notes').textContent = notes
document.querySelector('#meta').textContent = incremental
  ? `本次为增量更新，下载约 ${size}。选择“现在升级”会立即安装并重启；选择“退出应用后升级”会在退出后自动完成。`
  : '本次需要更新完整客户端。选择“现在升级”会立即安装并重启；选择“退出应用后升级”会在退出后自动完成。'

const choose = (action) => {
  document.querySelectorAll('button').forEach((button) => {
    button.disabled = true
  })
  window.regentUpdate.choose(action)
}

document.querySelector('#skip').addEventListener('click', () => choose('skip'))
document.querySelector('#deferred').addEventListener('click', () => choose('defer'))
document.querySelector('#install').addEventListener('click', () => choose('install'))
