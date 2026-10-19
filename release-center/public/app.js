const elements = {
  gitHead: document.querySelector('#git-head'),
  currentVersion: document.querySelector('#current-version'),
  nextVersion: document.querySelector('#next-version'),
  history: document.querySelector('#release-history'),
  version: document.querySelector('#version'),
  instruction: document.querySelector('#instruction'),
  generateButton: document.querySelector('#generate-button'),
  generateStatus: document.querySelector('#generate-status'),
  error: document.querySelector('#error'),
  workspaceWarning: document.querySelector('#workspace-warning'),
  preview: document.querySelector('#preview'),
  previewVersion: document.querySelector('#preview-version'),
  summary: document.querySelector('#summary'),
  highlights: document.querySelector('#highlights'),
  addHighlight: document.querySelector('#add-highlight'),
  deploy: document.querySelector('#deploy'),
  pushTag: document.querySelector('#push-tag'),
  tagPreview: document.querySelector('#tag-preview'),
  confirmReview: document.querySelector('#confirm-review'),
  publishButton: document.querySelector('#publish-button'),
  jobPanel: document.querySelector('#job-panel'),
  jobTitle: document.querySelector('#job-title'),
  jobStatus: document.querySelector('#job-status'),
  jobLog: document.querySelector('#job-log'),
}

let previewVersion = ''
let pollTimer = null
let workspaceDirty = false

function showError(message) {
  elements.error.textContent = message
  elements.error.hidden = !message
}

function setBusy(busy) {
  elements.generateButton.disabled = busy
  elements.version.disabled = busy
  elements.instruction.disabled = busy
  elements.generateStatus.textContent = busy ? 'Codex 正在分析项目…' : ''
}

function updateConfirmState() {
  elements.publishButton.disabled = !(
    previewVersion
    && !workspaceDirty
    && elements.confirmReview.checked
    && elements.highlights.children.length > 0
  )
}

function highlightRow(item = { title: '', description: '' }) {
  const row = document.createElement('div')
  row.className = 'highlight-row'

  const title = document.createElement('input')
  title.type = 'text'
  title.className = 'highlight-title'
  title.placeholder = '更新标题'
  title.value = item.title

  const description = document.createElement('textarea')
  description.className = 'highlight-description'
  description.rows = 2
  description.placeholder = '用户能感知到的具体变化'
  description.value = item.description

  const remove = document.createElement('button')
  remove.type = 'button'
  remove.className = 'icon-button'
  remove.title = '删除更新项'
  remove.setAttribute('aria-label', '删除更新项')
  remove.textContent = '×'
  remove.addEventListener('click', () => {
    row.remove()
    updateConfirmState()
  })

  title.addEventListener('input', updateConfirmState)
  description.addEventListener('input', updateConfirmState)
  row.append(title, description, remove)
  return row
}

function renderPreview(preview) {
  previewVersion = preview.version
  elements.preview.hidden = false
  elements.previewVersion.textContent = preview.version
  elements.summary.value = preview.summary
  elements.highlights.replaceChildren(
    ...preview.highlights.map((item) => highlightRow(item)),
  )
  elements.tagPreview.textContent = preview.version
  elements.confirmReview.checked = false
  updateConfirmState()
  elements.preview.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function readPreview() {
  const summary = elements.summary.value.trim()
  if (!summary) throw new Error('版本摘要不能为空')
  const highlights = [...elements.highlights.children].map((row, index) => {
    const title = row.querySelector('.highlight-title').value.trim()
    const description = row.querySelector('.highlight-description').value.trim()
    if (!title || !description) throw new Error(`第 ${index + 1} 个更新项不完整`)
    return { title, description }
  })
  if (highlights.length === 0) throw new Error('至少保留一个更新项')
  return { summary, highlights }
}

async function request(path, options) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.error || `HTTP ${response.status}`)
  return body
}

async function loadState() {
  const state = await request('/api/state')
  workspaceDirty = state.dirty === true
  elements.gitHead.textContent = state.gitHead
  elements.workspaceWarning.hidden = !workspaceDirty
  elements.currentVersion.textContent = state.currentVersion
  elements.nextVersion.textContent = `下一个建议：${state.suggestedVersion}`
  if (!elements.version.value) elements.version.value = state.suggestedVersion
  elements.history.replaceChildren()
  for (const release of state.releases) {
    const item = document.createElement('div')
    item.className = 'history-item'
    const version = document.createElement('span')
    version.textContent = release.version
    const date = document.createElement('small')
    date.textContent = release.releasedAt
    item.append(version, date)
    elements.history.append(item)
  }
  updateConfirmState()
}

async function generatePreview() {
  showError('')
  elements.preview.hidden = true
  previewVersion = ''
  setBusy(true)
  try {
    const preview = await request('/api/generate', {
      method: 'POST',
      body: JSON.stringify({
        version: elements.version.value.trim(),
        instruction: elements.instruction.value.trim(),
      }),
    })
    renderPreview(preview)
  } catch (error) {
    showError(error.message)
  } finally {
    setBusy(false)
  }
}

function renderJob(job) {
  elements.jobPanel.hidden = false
  elements.jobPanel.dataset.status = job.status
  elements.jobTitle.textContent = `${job.version} 发布`
  elements.jobStatus.textContent = job.status === 'running'
    ? '执行中'
    : job.status === 'success'
      ? '发布完成'
      : '发布失败'
  elements.jobLog.textContent = job.logs.join('\n')
  elements.jobLog.scrollTop = elements.jobLog.scrollHeight
}

async function pollJob(jobId) {
  try {
    const job = await request(`/api/jobs/${encodeURIComponent(jobId)}`)
    renderJob(job)
    if (job.status === 'running') {
      pollTimer = setTimeout(() => pollJob(jobId), 1000)
      return
    }
    elements.publishButton.disabled = false
    if (job.status === 'success') {
      elements.jobPanel.scrollIntoView({ behavior: 'smooth', block: 'start' })
      await loadState()
    } else {
      showError(job.error || '发布失败，请查看执行日志')
    }
  } catch (error) {
    showError(error.message)
  }
}

async function publish() {
  showError('')
  try {
    const content = readPreview()
    if (!elements.confirmReview.checked) throw new Error('请先确认发布内容')
    elements.publishButton.disabled = true
    const result = await request('/api/publish', {
      method: 'POST',
      body: JSON.stringify({
        version: previewVersion,
        ...content,
        deploy: elements.deploy.checked,
        pushTag: elements.pushTag.checked,
      }),
    })
    elements.jobPanel.hidden = false
    elements.jobStatus.textContent = '准备中'
    elements.jobLog.textContent = '正在启动发布流程…'
    clearTimeout(pollTimer)
    await pollJob(result.jobId)
  } catch (error) {
    elements.publishButton.disabled = false
    showError(error.message)
  }
}

elements.generateButton.addEventListener('click', generatePreview)
elements.addHighlight.addEventListener('click', () => {
  elements.highlights.append(highlightRow())
  updateConfirmState()
})
elements.confirmReview.addEventListener('change', updateConfirmState)
elements.summary.addEventListener('input', updateConfirmState)
elements.version.addEventListener('input', () => {
  if (previewVersion && elements.version.value.trim() !== previewVersion) {
    elements.preview.hidden = true
    previewVersion = ''
    updateConfirmState()
  }
})
elements.publishButton.addEventListener('click', publish)

loadState().catch((error) => showError(error.message))
