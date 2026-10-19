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
  serverStatus: document.querySelector('#server-status'),
  serverUrl: document.querySelector('#server-url'),
  serverConnector: document.querySelector('#server-connector'),
  serverQueue: document.querySelector('#server-queue'),
  serverRefresh: document.querySelector('#server-refresh'),
  serverDeploy: document.querySelector('#server-deploy'),
  preview: document.querySelector('#preview'),
  previewVersion: document.querySelector('#preview-version'),
  summary: document.querySelector('#summary'),
  highlights: document.querySelector('#highlights'),
  addHighlight: document.querySelector('#add-highlight'),
  deploy: document.querySelector('#deploy'),
  pushTag: document.querySelector('#push-tag'),
  tagPreview: document.querySelector('#tag-preview'),
  confirmReview: document.querySelector('#confirm-review'),
  workspaceRefresh: document.querySelector('#workspace-refresh'),
  publishBlocker: document.querySelector('#publish-blocker'),
  publishButton: document.querySelector('#publish-button'),
  jobPanel: document.querySelector('#job-panel'),
  jobTitle: document.querySelector('#job-title'),
  jobStatus: document.querySelector('#job-status'),
  jobLog: document.querySelector('#job-log'),
}

let previewVersion = ''
let pollTimer = null
let workspaceDirty = false
let draftStale = false
let draftSaveTimer = null

const DIRTY_WARNING = '当前工作区有未提交改动。版本内容和预览可以生成，但创建 Git 标签前必须先提交代码。'
const STALE_DRAFT_WARNING = '这份发布预览生成后代码提交有更新，请确认当前内容仍覆盖最新改动，或重新生成。'

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
  const ready = (
    previewVersion
    && !workspaceDirty
    && elements.confirmReview.checked
    && elements.highlights.children.length > 0
  )
  elements.publishButton.disabled = !ready
  elements.workspaceRefresh.hidden = !(workspaceDirty || draftStale)

  let blocker = ''
  if (previewVersion && workspaceDirty) {
    blocker = '当前工作区有未提交改动。提交代码后点击“重新检查工作区”，无需刷新整页。'
  } else if (previewVersion && elements.highlights.children.length === 0) {
    blocker = '至少保留一个更新项后才能发布。'
  } else if (previewVersion && !elements.confirmReview.checked) {
    blocker = '勾选“我已核对”后才能确认发布。'
  } else if (previewVersion && draftStale) {
    blocker = '预览生成后代码有更新，请确认当前内容仍覆盖最新改动。'
  }
  elements.publishBlocker.textContent = blocker
  elements.publishBlocker.hidden = !blocker
}

function updateWorkspaceWarning() {
  const messages = []
  if (workspaceDirty) messages.push(DIRTY_WARNING)
  if (draftStale) messages.push(STALE_DRAFT_WARNING)
  elements.workspaceWarning.textContent = messages.join(' ')
  elements.workspaceWarning.hidden = messages.length === 0
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
    scheduleDraftSave()
  })

  title.addEventListener('input', () => {
    updateConfirmState()
    scheduleDraftSave()
  })
  description.addEventListener('input', () => {
    updateConfirmState()
    scheduleDraftSave()
  })
  row.append(title, description, remove)
  return row
}

function renderPreview(preview, { focus = true } = {}) {
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
  if (focus) elements.preview.scrollIntoView({ behavior: 'smooth', block: 'start' })
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

async function persistDraft() {
  if (!previewVersion) return
  try {
    const content = readPreview()
    await request('/api/draft', {
      method: 'POST',
      body: JSON.stringify({
        version: previewVersion,
        ...content,
        deploy: elements.deploy.checked,
        pushTag: elements.pushTag.checked,
      }),
    })
  } catch (error) {
    // 输入尚未完整时草稿暂时无法保存，等下一次有效输入继续。
    if (!/不完整|不能为空|至少保留/.test(error.message)) {
      console.warn(`发布草稿保存失败：${error.message}`)
    }
  }
}

function scheduleDraftSave() {
  if (!previewVersion) return
  clearTimeout(draftSaveTimer)
  draftSaveTimer = setTimeout(() => {
    void persistDraft()
  }, 350)
}

function clearPersistedDraft() {
  clearTimeout(draftSaveTimer)
  draftSaveTimer = null
  void request('/api/draft', { method: 'DELETE' }).catch((error) => {
    console.warn(`发布草稿清理失败：${error.message}`)
  })
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

function applyWorkspaceState(state) {
  workspaceDirty = state.dirty === true
  draftStale = state.draftStale === true
  updateWorkspaceWarning()
  updateConfirmState()
}

async function refreshWorkspaceState() {
  elements.workspaceRefresh.disabled = true
  try {
    const state = await request('/api/state')
    applyWorkspaceState(state)
    return state
  } finally {
    elements.workspaceRefresh.disabled = false
  }
}

async function loadState() {
  const state = await request('/api/state')
  applyWorkspaceState(state)
  elements.gitHead.textContent = state.gitHead
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
  if (state.draft) {
    elements.version.value = state.draft.version
    elements.deploy.checked = state.draft.deploy !== false
    elements.pushTag.checked = state.draft.pushTag === true
    renderPreview(state.draft, { focus: false })
  } else {
    elements.preview.hidden = true
    previewVersion = ''
    elements.confirmReview.checked = false
  }
  updateConfirmState()
  try {
    const lastJob = await request('/api/jobs/last')
    if (lastJob?.status && lastJob.status !== 'running') renderJob(lastJob)
  } catch {
    // 还没有历史发布任务。
  }
}

async function loadServerStatus() {
  elements.serverRefresh.disabled = true
  try {
    const status = await request('/api/server/status')
    elements.serverStatus.className =
      `badge ${status.reachable && status.connectorOnline ? 'success' : 'danger'}`
    elements.serverStatus.textContent = status.reachable && status.connectorOnline
      ? '在线'
      : status.reachable
        ? '连接器离线'
        : '服务器不可达'
    elements.serverUrl.textContent = status.url || '读取失败'
    elements.serverConnector.textContent = status.connectorOnline ? '在线' : '离线'
    elements.serverQueue.textContent = String(status.queueDepth ?? 0)
  } catch (error) {
    elements.serverStatus.className = 'badge danger'
    elements.serverStatus.textContent = '状态读取失败'
    elements.serverUrl.textContent = error.message
    elements.serverConnector.textContent = '—'
    elements.serverQueue.textContent = '—'
  } finally {
    elements.serverRefresh.disabled = false
  }
}

async function deployServer() {
  if (!window.confirm('确认重新构建并发布中继器、升级服务器前端并重启服务？')) return
  showError('')
  elements.serverDeploy.disabled = true
  try {
    const result = await request('/api/server/deploy', { method: 'POST', body: '{}' })
    elements.jobPanel.hidden = false
    elements.jobStatus.textContent = '准备中'
    elements.jobLog.textContent = '正在启动服务器发布流程…'
    clearTimeout(pollTimer)
    await pollJob(result.jobId)
  } catch (error) {
    elements.serverDeploy.disabled = false
    showError(error.message)
  }
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
    draftStale = false
    elements.deploy.checked = preview.deploy !== false
    elements.pushTag.checked = preview.pushTag === true
    updateWorkspaceWarning()
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
  elements.jobTitle.textContent = job.title || `${job.version} 发布`
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
      clearTimeout(draftSaveTimer)
      draftSaveTimer = null
      elements.jobPanel.scrollIntoView({ behavior: 'smooth', block: 'start' })
      await loadState()
      if (job.kind === 'server-deploy') await loadServerStatus()
    } else {
      showError(job.error || '发布失败，请查看执行日志')
    }
    elements.serverDeploy.disabled = false
  } catch (error) {
    elements.serverDeploy.disabled = false
    showError(error.message)
  }
}

async function publish() {
  showError('')
  try {
    const content = readPreview()
    if (!elements.confirmReview.checked) throw new Error('请先确认发布内容')
    const state = await refreshWorkspaceState()
    if (state.dirty === true) {
      throw new Error('工作区有未提交改动，请先提交代码后再发布')
    }
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
  scheduleDraftSave()
})
elements.confirmReview.addEventListener('change', () => {
  updateConfirmState()
  if (elements.confirmReview.checked) {
    void refreshWorkspaceState().catch((error) => showError(error.message))
  }
})
elements.workspaceRefresh.addEventListener('click', () => {
  showError('')
  void refreshWorkspaceState().catch((error) => showError(error.message))
})
elements.summary.addEventListener('input', () => {
  updateConfirmState()
  scheduleDraftSave()
})
elements.deploy.addEventListener('change', scheduleDraftSave)
elements.pushTag.addEventListener('change', scheduleDraftSave)
elements.version.addEventListener('input', () => {
  if (previewVersion && elements.version.value.trim() !== previewVersion) {
    elements.preview.hidden = true
    previewVersion = ''
    draftStale = false
    updateWorkspaceWarning()
    clearPersistedDraft()
    updateConfirmState()
  }
})
elements.publishButton.addEventListener('click', publish)
elements.serverRefresh.addEventListener('click', loadServerStatus)
elements.serverDeploy.addEventListener('click', deployServer)

Promise.all([loadState(), loadServerStatus()]).catch((error) => showError(error.message))
