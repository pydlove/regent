<script setup lang="ts">
import {
  FilePlus2,
  FileText,
  ImagePlus,
  Library,
  Mic,
  Plus,
  Square,
  Trash2,
  TriangleAlert,
  X,
} from '@lucide/vue'
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'

import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_BYTES, uploadTaskAttachment } from '@/api/attachments'
import { ApiError } from '@/api/http'
import { bindTaskPlugins, listPlugins } from '@/api/plugins'
import { listCodeStandards } from '@/api/codeStandards'
import { createTask } from '@/api/tasks'
import {
  ACCEPTED_DOCUMENT_EXTENSIONS,
  MAX_DOCUMENT_BYTES,
  MAX_DOCUMENTS_PER_TASK,
  bindLibraryDocuments,
  listFileLibrary,
  uploadTaskDocument,
} from '@/api/documents'
import PluginPicker from '@/components/business/PluginPicker.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Progress from '@/components/ui/progress/Progress.vue'
import Select from '@/components/ui/select/Select.vue'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useSpeechToChinese, type SpeechLanguage } from '@/composables/useSpeechToChinese'
import { useTaskDraftAutosave } from '@/composables/useTaskDraftAutosave'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { CodeStandard, LibraryDocument, PluginSummary } from '@/types/api'
import {
  filesFromClipboard,
  isDocumentFile,
} from '@/utils/documentFiles'
import { formatBytes } from '@/utils/format'
import { MAX_TASK_PLUGINS } from '@/utils/pluginCatalog'

/*
 * 新建任务。
 *
 * 这里刻意**不是**「新建需求」：需求要先被拆解成任务，那条链路还没接。而这个弹框交付的是
 * 一条马上能派发的任务 —— 直接来自人的一句话，来源标记 MANUAL。把入口叫做「需求」而实际
 * 建的是任务，会让人以为系统已经会拆解了。
 *
 * 几件按反馈定下来的事，都不是审美，是可用性：
 *
 * 1. **没有标题字段**。任务标题由后端从指令首行推导。让人先写标题再写指令，
 *    等于让他把同一句话抄两遍，抄出来的那份还会和正文越长越不像。
 * 2. **宽度 640px（layout=form + size=large）**。384px 下指令框每行只放十几个字，
 *    写一段验收标准要来回折行，还要贴图。
 * 3. **能贴图**。有些任务光靠文字说不清（报错截图、设计稿、手画的原型），图片当真喂给
 *    codex（`exec -i`，见 CodexCliAdapter）。白名单与上限的判据在后端，这里只做即时反馈。
 * 4. **表单排法是 macOS 对话框的样子**：标签右对齐在左列、控件在右列、说明落在控件下方，
 *    见 DialogField 的注释。这份弹框是这个排法最长的用例，别退回「标签在上」。
 * 5. **不在这里注册项目**。项目注册的入口在工具栏（项目下拉旁边）与项目页 ——
 *    项目比任务长命，把它挂在「新建任务 → 没有项目」这条一次性路径上是不合理的。
 *    只有「一个项目都没有」的死结状态才给一个直达按钮，否则人只能关掉弹框去找入口。
 *
 * 图片的时序：先建任务、关闭弹框，再在后台逐张上传。附件接口挂在任务下
 * （`POST /api/tasks/{id}/attachments`），所以「贴了图但任务没建出来」这种半成品状态
 * 在库里不可能出现。上传结果通过 toast 汇报，不让人守着弹框等完整个队列。
 *
 * 表单正文与选项走服务端草稿，关闭弹框后下次还会回到原处；图片仍只在本次弹框中保留，
 * 因为附件必须挂在已创建的任务下。
 */
const emit = defineEmits<{
  created: [{ id: number; title: string }]
  /** 图片传完（含部分失败后重试成功）再发一次：详情侧栏据此重拉缩略图，不必重新选中任务 */
  uploaded: [{ id: number }]
}>()

const open = defineModel<boolean>('open', { default: false })

const toast = useToast()

/*
 * 项目列表放在任务 store 里而不是这个弹框里：工具栏的项目下拉要的是同一份数据，
 * 刚注册的项目应当立刻出现在这里，而不是等下次刷新。
 */
const store = useTaskStore()
const projects = computed(() => store.projects)
const loadingProjects = ref(false)

/*
 * 项目区三态，规则是「上面选过就不用再选」：
 *   - 工具栏选着具体项目 → 只显示一行，不再问一遍；
 *   - 工具栏是「全部项目」→ 让他在弹框里挑一个具体的（项目多于一个才给下拉；只有一个就直接用它，
 *     让人在只有一个选项的下拉里点一下不产生任何信息）；
 *   - 一个项目都没有 → 提示去注册（入口在工具栏，见文件头注释第 5 条）。
 */
const pickedProjectId = ref<number | null>(null)

const showProjectSelect = computed(
  () =>
    projects.value.length > 1 &&
    (store.projectId === null ||
      (pickedProjectId.value !== null && pickedProjectId.value !== store.projectId)),
)

const projectId = computed<number | null>(() => {
  if (showProjectSelect.value) return pickedProjectId.value
  if (pickedProjectId.value !== null) return pickedProjectId.value
  if (store.projectId !== null) return store.projectId
  if (projects.value.length === 1) return projects.value[0].id
  return null
})

const fixedProject = computed(
  () => projects.value.find((project) => project.id === projectId.value) ?? null,
)

const projectHint = computed(() => {
  if (store.projectId !== null && projectId.value !== store.projectId) {
    return '已恢复草稿中的项目'
  }
  return store.projectId !== null ? '跟随工具栏的项目筛选' : '当前只有一个项目，已自动选上'
})

const projectItems = computed(() =>
  projects.value.map((project) => ({
    // Select 的值是字符串，转成 ID 的活在这一层干完，别把字符串漏进请求体
    value: String(project.id),
    label: project.enabled ? `${project.name}（${project.code}）` : `${project.name}（已暂停）`,
  })),
)

// Select 只认字符串（reka-ui 的类型如此），这一层负责与 number 互转
const pickedProjectValue = computed({
  get: () => (pickedProjectId.value === null ? undefined : String(pickedProjectId.value)),
  set: (value: string | undefined) => {
    pickedProjectId.value = value === undefined ? null : Number(value)
  },
})

const prompt = ref('')
const priority = ref('0')
const speechLanguage = ref<SpeechLanguage>('zh-CN')
const speechLanguageItems = [
  { value: 'zh-CN', label: '普通话' },
  { value: 'en-US', label: 'English' },
  { value: 'ja-JP', label: '日本語' },
  { value: 'ko-KR', label: '한국어' },
]
const speech = useSpeechToChinese()
const canUseSpeech = computed(
  () =>
    speech.supported &&
    speech.secureContext &&
    phase.value === 'editing' &&
    autosaveReady.value,
)
const speechStatus = computed(() => {
  if (!speech.supported) return '当前浏览器不支持语音输入'
  if (!speech.secureContext) return '语音输入需要 HTTPS，或在本机 localhost 使用'
  if (speech.error.value) return speech.error.value
  if (speech.listening.value && speech.translating.value) return '正在录音并实时转写…'
  if (speech.translating.value) return '正在用本机 Whisper 转写…'
  if (speech.listening.value) return '正在录音，说完后点击停止'
  if (speech.translationFallback.value) return '翻译服务不可用，已保留识别原文'
  return speechLanguage.value === 'zh-CN'
    ? '录音停止后由本机 Whisper 写入指令'
    : '录音后转写，并尝试翻译成中文'
})
const plugins = ref<PluginSummary[]>([])
const selectedPluginIds = ref<number[]>([])
const loadingPlugins = ref(false)
const pluginsError = ref<string | null>(null)

/** editing 可以改；creating 已提交，正在等创建接口返回 */
type Phase = 'editing' | 'creating'
const phase = ref<Phase>('editing')
const error = ref<string | null>(null)
const autosaveReady = ref(false)
let initializeSequence = 0

const draftAutosave = useTaskDraftAutosave()
const {
  draft: savedDraft,
  loading: loadingDraft,
  state: draftState,
  error: draftError,
  savedAt: draftSavedAt,
} = draftAutosave

// —— 待上传的图片 ——
type PendingImage = {
  localId: number
  file: File
  /** 本地预览地址（objectURL）。提交完或关弹框时必须 revoke，否则这些 blob 会一直占着内存 */
  previewUrl: string
  progress: number
  status: 'idle' | 'uploading' | 'done' | 'failed'
  error: string | null
}

type PendingDocument = {
  localId: number
  file: File
  progress: number
  status: 'idle' | 'uploading' | 'done' | 'failed'
  saveToLibrary: boolean
  error: string | null
}

const pending = ref<PendingImage[]>([])
let nextLocalId = 1
const dragging = ref(false)
const documentDragging = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const pendingBytes = computed(() => pending.value.reduce((sum, item) => sum + item.file.size, 0))
const pendingDocuments = ref<PendingDocument[]>([])
const documentInput = ref<HTMLInputElement | null>(null)
const pendingDocumentBytes = computed(
  () => pendingDocuments.value.reduce((sum, item) => sum + item.file.size, 0),
)
const libraryDocuments = ref<LibraryDocument[]>([])
const selectedLibraryDocumentIds = ref<number[]>([])
const libraryLoading = ref(false)
const libraryError = ref<string | null>(null)
const codeStandards = ref<CodeStandard[]>([])
const selectedCodeStandardIds = ref<number[]>([])
const codeStandardsLoading = ref(false)
const codeStandardsError = ref<string | null>(null)

const canSubmit = computed(
  () =>
    phase.value === 'editing' &&
    projectId.value !== null &&
    prompt.value.trim().length > 0 &&
    !loadingDraft.value &&
    !codeStandardsLoading.value &&
    !codeStandardsError.value &&
    autosaveReady.value &&
    !speech.listening.value &&
    !speech.translating.value,
)

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const { openDialog: openProjectDialog, lastRegistered } = useProjectDialog()

// 开着这个弹框时去注册了项目：把新项目选上，别让人回来还得再点一次下拉
watch(lastRegistered, (project) => {
  if (project && open.value) pickedProjectId.value = project.id
})

const releasePreviews = () => {
  for (const item of pending.value) URL.revokeObjectURL(item.previewUrl)
  pending.value = []
  pendingDocuments.value = []
}

const loadProjects = async () => {
  loadingProjects.value = true
  try {
    await store.loadProjects()
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loadingProjects.value = false
  }
}

const loadPlugins = async () => {
  loadingPlugins.value = true
  pluginsError.value = null
  try {
    plugins.value = (await listPlugins()).filter((plugin) => plugin.enabled)
  } catch (e) {
    // 插件是可选项，接口暂不可用不能把整个任务创建入口判死
    plugins.value = []
    pluginsError.value = messageOf(e)
  } finally {
    loadingPlugins.value = false
  }
}

const loadLibrary = async () => {
  const targetProjectId = projectId.value
  if (targetProjectId === null || !open.value) {
    libraryDocuments.value = []
    selectedLibraryDocumentIds.value = []
    return
  }
  libraryLoading.value = true
  libraryError.value = null
  try {
    libraryDocuments.value = await listFileLibrary(targetProjectId)
    const available = new Set(libraryDocuments.value.map((document) => document.id))
    selectedLibraryDocumentIds.value = selectedLibraryDocumentIds.value.filter((id) => available.has(id))
  } catch (e) {
    libraryDocuments.value = []
    selectedLibraryDocumentIds.value = []
    libraryError.value = messageOf(e)
  } finally {
    libraryLoading.value = false
  }
}

const loadCodeStandards = async () => {
  const targetProjectId = projectId.value
  if (targetProjectId === null || !open.value) {
    codeStandards.value = []
    selectedCodeStandardIds.value = []
    return
  }
  codeStandardsLoading.value = true
  codeStandardsError.value = null
  try {
    codeStandards.value = (await listCodeStandards(targetProjectId))
      .filter((standard) => standard.enabled && !standard.archived)
    selectedCodeStandardIds.value = codeStandards.value
      .filter((standard) => standard.defaultApply)
      .map((standard) => standard.id)
  } catch (e) {
    codeStandards.value = []
    selectedCodeStandardIds.value = []
    codeStandardsError.value = messageOf(e)
  } finally {
    codeStandardsLoading.value = false
  }
}

const draftFields = () => ({
  projectId: projectId.value,
  prompt: prompt.value,
  priority: Number(priority.value) || 0,
  pluginIds: [...selectedPluginIds.value],
})

const defaultProjectId = () =>
  store.projectId ?? (projects.value.length === 1 ? projects.value[0].id : null)

const initializeDraft = async () => {
  const sequence = ++initializeSequence
  autosaveReady.value = false
  await Promise.all([loadProjects(), loadPlugins(), draftAutosave.load()])
  if (sequence !== initializeSequence || !open.value) return
  const restored = savedDraft.value
  prompt.value = restored?.prompt ?? ''
  priority.value = String(restored?.priority ?? 0)
  selectedPluginIds.value = restored?.pluginIds ?? []
  const restoredProject = restored?.projectId ?? null
  pickedProjectId.value =
    restoredProject !== null && projects.value.some((project) => project.id === restoredProject)
      ? restoredProject
      : defaultProjectId()
  await nextTick()
  if (sequence === initializeSequence && open.value) {
    autosaveReady.value = true
    void loadLibrary()
    void loadCodeStandards()
  }
}

// 每次打开恢复当前草稿；空表单不会因为默认项目或初始化赋值而生成一份空草稿。
watch(open, (value) => {
  if (!value) {
    initializeSequence++
    speech.abort()
    documentDragging.value = false
    if (phase.value === 'editing' && autosaveReady.value) {
      void draftAutosave.flush(draftFields())
    }
    autosaveReady.value = false
    return
  }
  draftAutosave.resume()
  speech.reset()
  prompt.value = ''
  priority.value = '0'
  error.value = null
  phase.value = 'editing'
  pickedProjectId.value = null
  dragging.value = false
  documentDragging.value = false
  pluginsError.value = null
  selectedPluginIds.value = []
  releasePreviews()
  libraryDocuments.value = []
  selectedLibraryDocumentIds.value = []
  codeStandards.value = []
  selectedCodeStandardIds.value = []
  codeStandardsError.value = null
  void initializeDraft()
})

watch(projectId, () => {
  if (open.value) {
    void loadLibrary()
    void loadCodeStandards()
  }
})

watch(
  [() => projectId.value, prompt, priority, () => selectedPluginIds.value.join(',')],
  () => {
    if (!open.value || !autosaveReady.value || phase.value !== 'editing') return
    draftAutosave.schedule(draftFields())
  },
)

watch(speech.translationFallback, (fallback) => {
  if (fallback) {
    toast.warning(
      '语音已识别，但当前浏览器无法翻译成中文',
      '已保留识别原文；建议使用最新版 Chrome 或 Edge',
    )
  }
})

const appendSegment = (base: string, text: string) => {
  const current = base.trimEnd()
  if (!current) return text
  const needsSpace = /[A-Za-z0-9]$/.test(current) && /^[A-Za-z0-9]/.test(text)
  return `${current}${needsSpace ? ' ' : ''}${text}`
}

const displayedPrompt = computed({
  get: () => {
    const interim = speech.interim.value.trim()
    return interim ? appendSegment(prompt.value, interim) : prompt.value
  },
  set: (value: string) => {
    prompt.value = value
  },
})

const appendSpeechText = (text: string) => {
  const normalized = text.trim()
  if (!normalized) return
  prompt.value = appendSegment(prompt.value, normalized)
}

const toggleSpeech = () => {
  if (!canUseSpeech.value) return
  if (speech.listening.value) {
    speech.stop()
    return
  }
  speech.start(speechLanguage.value, appendSpeechText)
}

const addFiles = (files: File[]) => {
  for (const file of files) {
    if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
      toast.warning(`「${file.name}」不是支持的图片`, '只收 PNG / JPEG / GIF / WebP 四种')
      continue
    }
    if (file.size > MAX_IMAGE_BYTES) {
      toast.warning(
        `「${file.name}」太大（${formatBytes(file.size)}）`,
        `单张上限 ${formatBytes(MAX_IMAGE_BYTES)}，压缩后再试`,
      )
      continue
    }
    // 粘贴与拖放都容易重复触发同一张图，这里按「同名同大小」去重
    if (pending.value.some((item) => item.file.name === file.name && item.file.size === file.size)) {
      toast.show(`「${file.name}」已经在列表里了`)
      continue
    }
    pending.value.push({
      localId: nextLocalId++,
      file,
      previewUrl: URL.createObjectURL(file),
      progress: 0,
      status: 'idle',
      error: null,
    })
  }
}

const onPick = (event: Event) => {
  const input = event.target as HTMLInputElement
  addFiles(Array.from(input.files ?? []))
  // 清掉 value：连着选同一个文件两次时 change 不会再触发，看起来像「点了没反应」
  input.value = ''
}

const addDocuments = (files: File[]) => {
  for (const file of files) {
    if (pendingDocuments.value.length + selectedLibraryDocumentIds.value.length
        >= MAX_DOCUMENTS_PER_TASK) {
      toast.warning(
        `单个任务最多 ${MAX_DOCUMENTS_PER_TASK} 份文档`,
        '可以移除一部分后再添加',
      )
      break
    }
    if (!isDocumentFile(file)) {
      toast.warning(
        `「${file.name}」不是支持的文档`,
        `支持 ${ACCEPTED_DOCUMENT_EXTENSIONS.join('、')}`,
      )
      continue
    }
    if (file.size > MAX_DOCUMENT_BYTES) {
      toast.warning(
        `「${file.name}」太大（${formatBytes(file.size)}）`,
        `单份上限 ${formatBytes(MAX_DOCUMENT_BYTES)}`,
      )
      continue
    }
    if (
      pendingDocuments.value.some(
        (item) => item.file.name === file.name && item.file.size === file.size,
      )
    ) {
      toast.show(`「${file.name}」已经在文档列表里了`)
      continue
    }
    pendingDocuments.value.push({
      localId: nextLocalId++,
      file,
      progress: 0,
      status: 'idle',
      saveToLibrary: false,
      error: null,
    })
  }
}

const onPickDocuments = (event: Event) => {
  const input = event.target as HTMLInputElement
  addDocuments(Array.from(input.files ?? []))
  input.value = ''
}

const removeDocument = (item: PendingDocument) => {
  if (item.status === 'uploading') return
  pendingDocuments.value = pendingDocuments.value.filter((entry) => entry.localId !== item.localId)
}

const toggleLibraryDocument = (id: number, checked: boolean | 'indeterminate') => {
  if (checked === true) {
    if (!selectedLibraryDocumentIds.value.includes(id)) {
      if (pendingDocuments.value.length + selectedLibraryDocumentIds.value.length
          >= MAX_DOCUMENTS_PER_TASK) {
        toast.warning(
          `单个任务最多 ${MAX_DOCUMENTS_PER_TASK} 份文档`,
          '已选文件与待上传文件合计不能超过上限',
        )
        return
      }
      selectedLibraryDocumentIds.value.push(id)
    }
    return
  }
  selectedLibraryDocumentIds.value = selectedLibraryDocumentIds.value.filter(
    (documentId) => documentId !== id,
  )
}

const toggleCodeStandard = (id: number, checked: boolean | 'indeterminate') => {
  selectedCodeStandardIds.value = checked === true
    ? [...new Set([...selectedCodeStandardIds.value, id])]
    : selectedCodeStandardIds.value.filter((item) => item !== id)
}

const onPaste = (event: ClipboardEvent) => {
  const data = event.clipboardData
  if (!data) return
  const files = filesFromClipboard(data)
  if (files.length === 0) return
  const images = files.filter((file) => ACCEPTED_IMAGE_TYPES.includes(file.type))
  const documents = files.filter((file) => isDocumentFile(file))
  if (images.length > 0) addFiles(images)
  if (documents.length > 0) addDocuments(documents)
  const unknown = files.filter(
    (file) => !ACCEPTED_IMAGE_TYPES.includes(file.type) && !isDocumentFile(file),
  )
  if (unknown.length > 0) {
    toast.warning(
      `${unknown.length} 个文件没有加入任务`,
      '图片和文档请使用各自支持的文件类型',
    )
  }
  // 文档通常从文件管理器复制，粘贴出来的文本往往只是文件名或路径，不应混入指令。
  if (documents.length > 0 || data.getData('text/plain').length === 0) {
    event.preventDefault()
  }
}

const addDroppedFiles = (files: File[]) => {
  const images = files.filter((file) => ACCEPTED_IMAGE_TYPES.includes(file.type))
  const documents = files.filter((file) => isDocumentFile(file))
  if (images.length > 0) addFiles(images)
  if (documents.length > 0) addDocuments(documents)
  const unknown = files.filter(
    (file) => !ACCEPTED_IMAGE_TYPES.includes(file.type) && !isDocumentFile(file),
  )
  if (unknown.length > 0) {
    toast.warning(
      `${unknown.length} 个文件没有加入任务`,
      '图片和文档请使用各自支持的文件类型',
    )
  }
}

const onDrop = (event: DragEvent) => {
  dragging.value = false
  addDroppedFiles(Array.from(event.dataTransfer?.files ?? []))
}

const onDragLeave = (event: DragEvent) => {
  // dragleave 在子元素之间移动时也会冒上来，只看指针真的离开了整个面板
  const next = event.relatedTarget as Node | null
  if (!next || !(event.currentTarget as Node).contains(next)) dragging.value = false
}

const onDocumentDrop = (event: DragEvent) => {
  documentDragging.value = false
  addDroppedFiles(Array.from(event.dataTransfer?.files ?? []))
}

const onDocumentDragLeave = (event: DragEvent) => {
  const next = event.relatedTarget as Node | null
  if (!next || !(event.currentTarget as Node).contains(next)) documentDragging.value = false
}

const removeImage = (item: PendingImage) => {
  if (item.status === 'uploading') return
  URL.revokeObjectURL(item.previewUrl)
  pending.value = pending.value.filter((entry) => entry.localId !== item.localId)
}

/** 顺序上传，不并发：一张失败不会掩盖后续结果，也避免多张图同时占满有限带宽 */
const uploadAll = async (taskId: number, images: PendingImage[]) => {
  for (const item of images) {
    if (item.status === 'done') continue
    item.status = 'uploading'
    item.progress = 0
    item.error = null
    try {
      await uploadTaskAttachment(taskId, item.file, {
        onProgress: (percent) => {
          item.progress = percent
        },
      })
      item.status = 'done'
      item.progress = 100
    } catch (e) {
      item.status = 'failed'
      item.error = messageOf(e)
    }
  }
  const attached = images.filter((item) => item.status === 'done').length
  if (attached > 0) emit('uploaded', { id: taskId })
  return { attached, failed: images.length - attached }
}

const uploadDocuments = async (taskId: number, items: PendingDocument[]) => {
  let uploaded = 0
  let savedToLibrary = 0
  for (const item of items) {
    if (item.status === 'done') continue
    item.status = 'uploading'
    item.progress = 0
    item.error = null
    try {
      const document = await uploadTaskDocument(taskId, item.file, item.saveToLibrary, {
        onProgress: (percent) => {
          item.progress = percent
        },
      })
      item.status = 'done'
      item.progress = 100
      uploaded++
      if (document.savedToLibrary) savedToLibrary++
    } catch (e) {
      item.status = 'failed'
      item.error = messageOf(e)
    }
  }
  return { uploaded, savedToLibrary, failed: items.length - uploaded }
}

const announceDocumentResult = async (
  taskId: number,
  items: PendingDocument[],
  libraryIds: number[],
) => {
  let bound = 0
  if (libraryIds.length > 0) {
    try {
      bound = (await bindLibraryDocuments(taskId, libraryIds)).length
    } catch (e) {
      toast.warning(`#${taskId} 文件库文档绑定失败`, messageOf(e))
    }
  }
  const result = await uploadDocuments(taskId, items)
  const attached = bound + result.uploaded
  if (attached > 0) {
    const savedHint = result.savedToLibrary > 0
      ? `，其中 ${result.savedToLibrary} 份已保存到文件库`
      : ''
    toast.success(
      `${attached} 份文档已附上`,
      `#${taskId} 派发时会一并交给 codex${savedHint}`,
    )
  }
  if (result.failed > 0) {
    toast.warning(
      `${result.failed} 份文档没传上去`,
      `任务已创建，可以在右侧详情中查看并补充`,
    )
  }
  if (attached > 0) emit('uploaded', { id: taskId })
}

const close = () => {
  open.value = false
  phase.value = 'editing'
  releasePreviews()
}

const clearDraft = async () => {
  const sequence = initializeSequence
  try {
    await draftAutosave.remove()
    if (sequence !== initializeSequence || !open.value) {
      draftAutosave.resume()
      return
    }
    autosaveReady.value = false
    prompt.value = ''
    priority.value = '0'
    selectedPluginIds.value = []
    pickedProjectId.value = defaultProjectId()
    await nextTick()
    autosaveReady.value = true
    draftAutosave.resume()
    toast.show('草稿已清除')
  } catch (e) {
    draftAutosave.resume()
    toast.warning('草稿清除失败', messageOf(e))
  }
}

const draftStatusText = computed(() => {
  if (!autosaveReady.value || loadingDraft.value) return '正在恢复草稿…'
  if (draftState.value === 'pending') return '草稿等待保存…'
  if (draftState.value === 'saving') return '草稿保存中…'
  if (draftState.value === 'saved' && draftSavedAt.value) {
    const time = new Date(draftSavedAt.value).toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    })
    return `草稿已保存 ${time}`
  }
  if (draftState.value === 'error') return '草稿自动保存失败'
  return ''
})

const announceUploadResult = async (
  taskId: number,
  images: PendingImage[],
) => {
  const { attached, failed } = await uploadAll(taskId, images)
  if (attached > 0) {
    toast.success(`${attached} 张图已附上`, `#${taskId} 派发时会一并交给 codex`)
  }
  if (failed > 0) {
    toast.warning(`${failed} 张图没传上去`, `任务已创建，图片可以在右侧详情里补`)
  }
}

const submit = async () => {
  if (!canSubmit.value) return
  speech.abort()
  const target = projectId.value
  if (target === null) return
  error.value = null
  phase.value = 'creating'
  draftAutosave.suspend()
  const images = [...pending.value]
  const documents = [...pendingDocuments.value]
  const libraryIds = [...selectedLibraryDocumentIds.value]
  const standardIds = [...selectedCodeStandardIds.value]
  try {
    const task = await createTask({
      projectId: target,
      prompt: prompt.value.trim(),
      priority: Number(priority.value) || 0,
      codeStandardIds: standardIds,
    })
    if (selectedPluginIds.value.length > 0) {
      try {
        await bindTaskPlugins(task.id, selectedPluginIds.value)
      } catch (e) {
        toast.warning(`#${task.id} 插件绑定失败`, messageOf(e))
      }
    }
    try {
      await draftAutosave.remove()
    } catch (e) {
      toast.warning('任务已创建，但草稿未清除', messageOf(e))
    }
    emit('created', { id: task.id, title: task.title })
    close()
    // 弹框关闭后继续用提交时的文件快照上传，不能被下一次打开时的清空逻辑截断
    if (images.length > 0) void announceUploadResult(task.id, images)
    if (documents.length > 0 || libraryIds.length > 0) {
      void announceDocumentResult(task.id, documents, libraryIds)
    }
  } catch (e) {
    error.value = messageOf(e)
    phase.value = 'editing'
    draftAutosave.resume()
  }
}

onUnmounted(() => {
  if (open.value && phase.value === 'editing' && autosaveReady.value) {
    void draftAutosave.flush(draftFields())
  }
  releasePreviews()
})
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="新建任务"
    description="任务先进待执行队列；项目绑定本机代码目录后才会派发执行。"
  >
    <div
      class="flex flex-col gap-3"
      :class="dragging ? 'rounded-panel ring-2 ring-accent/50' : ''"
      @dragover.prevent="dragging = true"
      @dragleave="onDragLeave"
      @drop.prevent="onDrop"
    >
      <p v-if="loadingProjects && projects.length === 0" class="text-caption text-label-tertiary">
        正在读取项目列表…
      </p>

      <!-- 项目。规则见 script 里 showProjectSelect 的注释：上面选过就不再问一遍 -->
      <DialogField v-else-if="showProjectSelect" label="项目">
        <Select
          v-model="pickedProjectValue"
          :items="projectItems"
          placeholder="选择任务挂在哪个项目下"
          :disabled="!autosaveReady"
          block
        />
        <p
          v-if="store.projectId !== null && projectId !== store.projectId"
          class="mt-1 text-caption text-label-tertiary"
        >
          {{ projectHint }}
        </p>
      </DialogField>
      <DialogField v-else-if="fixedProject" label="项目" :hint="projectHint">
        <div class="flex h-control items-center gap-1.5">
          <span class="text-body text-label">{{ fixedProject.name }}</span>
        </div>
      </DialogField>
      <!-- 一个项目都没有是死结：任务必须挂在项目下，所以这里给一条直达的活路，
           其余情况不在这里新建项目（入口在工具栏，见文件头注释第 5 条） -->
      <DialogField
        v-else
        label="项目"
        hint="先创建研发项目，任务和立项都会挂在它下面"
      >
        <div class="flex h-control items-center gap-2">
          <span class="text-caption text-label-tertiary">还没有研发项目</span>
          <Button size="sm" class="ml-auto" @click="openProjectDialog()">
            <Icon :as="Plus" size="inline" />新建项目
          </Button>
        </div>
      </DialogField>

      <DialogField label="指令" hint="第一行会被拿去当任务标题，不用另写">
        <div class="mb-2 flex flex-wrap items-center gap-2">
          <Button
            size="sm"
            :variant="speech.listening.value ? 'primary' : 'default'"
            :disabled="!canUseSpeech"
            @click="toggleSpeech"
          >
            <Icon :as="speech.listening.value ? Square : Mic" size="inline" />
            {{
              speech.listening.value
                ? '停止录音'
                : speech.translating.value
                  ? '转写中…'
                  : '语音输入'
            }}
          </Button>
          <div class="w-28">
            <Select
              v-model="speechLanguage"
              :items="speechLanguageItems"
              size="sm"
              :disabled="!canUseSpeech || speech.listening.value || speech.translating.value"
            />
          </div>
          <span
            class="min-w-0 flex-1 truncate text-caption text-label-tertiary"
            :title="speechStatus"
            role="status"
            aria-live="polite"
          >
            {{ speechStatus }}
          </span>
        </div>
        <Textarea
          v-model="displayedPrompt"
          :rows="10"
          :disabled="
            phase !== 'editing' ||
            !autosaveReady ||
            speech.listening.value ||
            speech.translating.value
          "
          placeholder="交给 codex 的完整指令：要改哪里、验收标准是什么。截图可以直接按 ⌘V 贴进来"
          @paste="onPaste"
        />
      </DialogField>

      <!-- 拖放挂在整个面板上（人不会精确地往这一小条里丢文件），粘贴挂在指令框上 -->
      <DialogField
        label="图片"
        hint="支持拖放与 ⌘V 粘贴 —— 报错截图、设计稿这类说不清的东西直接贴进来；PNG / JPEG / GIF / WebP，单张上限 10.0 MB"
      >
        <div class="flex flex-wrap items-start gap-2">
          <div v-for="item in pending" :key="item.localId" class="group relative size-20">
            <div class="size-full overflow-hidden rounded-panel border border-separator-soft bg-content">
              <img
                :src="item.previewUrl"
                :alt="item.file.name"
                class="size-full object-cover"
                :class="item.status === 'uploading' ? 'opacity-40' : ''"
              />
              <Progress
                v-if="item.status === 'uploading'"
                :value="item.progress"
                class="absolute inset-x-0 bottom-0"
              />
              <span
                v-if="item.status === 'failed'"
                class="absolute inset-0 flex flex-col items-center justify-center gap-0.5 bg-danger/15 text-caption text-danger"
                :title="item.error ?? undefined"
              >
                <Icon :as="TriangleAlert" size="inline" />
                没传上
              </span>
            </div>

            <button
              v-if="item.status !== 'uploading'"
              type="button"
              title="移除这张图"
              class="absolute -right-1.5 -top-1.5 hidden size-5 items-center justify-center rounded-full bg-label text-white shadow-control group-hover:flex focus-visible:flex focus-visible:outline-none"
              @click="removeImage(item)"
            >
              <Icon :as="X" size="inline" />
            </button>
          </div>

          <button
            type="button"
            class="flex size-20 flex-col items-center justify-center gap-1 rounded-panel border border-dashed border-separator text-label-secondary transition-colors duration-100 ease-apple hover:border-accent/60 hover:text-accent focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
            :disabled="phase !== 'editing' || !autosaveReady"
            @click="fileInput?.click()"
          >
            <Icon :as="ImagePlus" size="large" />
            <span class="text-caption">添加图片</span>
          </button>

          <input
            ref="fileInput"
            type="file"
            :accept="ACCEPTED_IMAGE_TYPES.join(',')"
            multiple
            class="hidden"
            @change="onPick"
          />
        </div>

        <span v-if="pending.length > 0" class="tnum mt-1 block text-caption text-label-tertiary">
          已选 {{ pending.length }} 张 · {{ formatBytes(pendingBytes) }}
        </span>
      </DialogField>

      <DialogField
        label="文档"
        hint="支持 PDF、Office、OpenDocument、Markdown、文本与结构化文本；可点击选择、拖入，或按 ⌘V / Ctrl+V 粘贴。单份上限 25.0 MB，文档会作为只读资料交给执行器"
      >
        <div
          class="flex flex-col gap-2 rounded-panel border border-dashed p-2 transition-colors duration-120 ease-apple focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
          :class="
            documentDragging
              ? 'border-accent/70 bg-accent/5'
              : 'border-transparent'
          "
          tabindex="0"
          aria-label="任务文档上传区域，支持拖拽和粘贴"
          @dragover.prevent.stop="documentDragging = true"
          @dragleave.stop="onDocumentDragLeave"
          @drop.stop.prevent="onDocumentDrop"
          @paste="onPaste"
        >
          <div
            v-if="libraryLoading"
            class="flex h-9 items-center gap-2 text-caption text-label-tertiary"
          >
            <Icon :as="Library" size="inline" />
            正在读取项目文件库…
          </div>
          <div
            v-else-if="libraryDocuments.length > 0"
            class="rounded-panel border border-separator-soft bg-content px-3 py-2"
          >
            <div class="mb-1.5 flex items-center gap-1.5 text-caption text-label-secondary">
              <Icon :as="Library" size="inline" />
              从文件库复用
              <span class="ml-auto tnum text-label-tertiary">
                已选 {{ selectedLibraryDocumentIds.length }} 份
              </span>
            </div>
            <div class="max-h-28 space-y-1 overflow-y-auto">
              <div
                v-for="document in libraryDocuments"
                :key="document.id"
                class="flex min-h-8 items-center gap-2 rounded-control px-1 hover:bg-surface"
              >
                <Checkbox
                  :model-value="selectedLibraryDocumentIds.includes(document.id)"
                  :aria-label="`复用 ${document.filename}`"
                  :disabled="phase !== 'editing'"
                  @update:model-value="toggleLibraryDocument(document.id, $event)"
                />
                <Icon :as="FileText" size="inline" class="shrink-0 text-label-tertiary" />
                <span class="min-w-0 flex-1 truncate text-caption text-label" :title="document.filename">
                  {{ document.filename }}
                </span>
                <span class="tnum shrink-0 text-caption text-label-tertiary">
                  {{ formatBytes(document.sizeBytes) }}
                </span>
              </div>
            </div>
          </div>
          <p
            v-else-if="libraryError"
            class="text-caption text-label-tertiary"
            :title="libraryError"
          >
            文件库暂时不可用，不影响本次上传任务文档
          </p>

          <div
            v-for="item in pendingDocuments"
            :key="item.localId"
            class="flex min-h-11 items-center gap-2 rounded-panel border border-separator-soft bg-content px-3 py-2"
          >
            <Icon :as="FileText" size="regular" class="shrink-0 text-label-tertiary" />
            <div class="min-w-0 flex-1">
              <p class="truncate text-caption text-label" :title="item.file.name">
                {{ item.file.name }}
              </p>
              <div class="mt-0.5 flex items-center gap-2">
                <span class="tnum text-caption text-label-tertiary">
                  {{ formatBytes(item.file.size) }}
                </span>
                <span v-if="item.status === 'failed'" class="truncate text-caption text-danger" :title="item.error ?? undefined">
                  {{ item.error ?? '上传失败' }}
                </span>
              </div>
              <Progress
                v-if="item.status === 'uploading'"
                :value="item.progress"
                class="mt-1"
              />
            </div>
            <Checkbox
              v-model="item.saveToLibrary"
              label="保存到文件库"
              :disabled="phase !== 'editing' || item.status === 'uploading'"
            />
            <button
              type="button"
              title="移除文档"
              class="flex size-6 shrink-0 items-center justify-center rounded-control text-label-tertiary hover:bg-surface hover:text-danger focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
              :disabled="item.status === 'uploading'"
              @click="removeDocument(item)"
            >
              <Icon :as="X" size="inline" />
            </button>
          </div>

          <div class="flex flex-wrap items-center gap-2">
            <Button
              size="sm"
              :disabled="phase !== 'editing' || !autosaveReady"
              @click="documentInput?.click()"
            >
              <Icon :as="FilePlus2" size="inline" />
              添加文档
            </Button>
            <span
              v-if="pendingDocuments.length > 0"
              class="tnum text-caption text-label-tertiary"
            >
              已选 {{ pendingDocuments.length }} 份 · {{ formatBytes(pendingDocumentBytes) }}
            </span>
          </div>
          <p class="text-caption text-label-tertiary">
            可直接拖入文档，也可聚焦此区域后按 ⌘V / Ctrl+V 粘贴文件
          </p>
          <input
            ref="documentInput"
            type="file"
            :accept="ACCEPTED_DOCUMENT_EXTENSIONS.join(',')"
            multiple
            class="hidden"
            @change="onPickDocuments"
          />
        </div>
      </DialogField>

      <DialogField
        label="开发规范"
        hint="默认引用项目开发规范并保存版本快照；本次可以取消不需要的条目"
      >
        <div class="rounded-panel border border-separator-soft bg-content px-3 py-2">
          <div class="flex items-center gap-2">
            <Icon :as="Library" size="inline" class="text-label-tertiary" />
            <span class="text-caption font-emphasis text-label-secondary">项目开发规范</span>
            <Badge class="ml-auto">{{ selectedCodeStandardIds.length }} / {{ codeStandards.length }}</Badge>
          </div>
          <p v-if="codeStandardsLoading" class="mt-2 text-caption text-label-tertiary">
            正在读取…
          </p>
          <p v-else-if="codeStandardsError" class="mt-2 text-caption text-label-tertiary">
            暂时无法确认项目规范。为避免漏用规范，本任务暂不能创建，请关闭后重试。
          </p>
          <p v-else-if="codeStandards.length === 0" class="mt-2 text-caption text-label-tertiary">
            当前项目还没有可引用的开发规范。
          </p>
          <div v-else class="mt-2 max-h-36 space-y-1 overflow-y-auto">
            <div
              v-for="standard in codeStandards"
              :key="standard.id"
              class="flex min-h-8 items-center gap-2 rounded-control px-1 hover:bg-surface"
            >
              <Checkbox
                :model-value="selectedCodeStandardIds.includes(standard.id)"
                :aria-label="`引用 ${standard.name}`"
                :disabled="phase !== 'editing'"
                @update:model-value="toggleCodeStandard(standard.id, $event)"
              />
              <Icon :as="FileText" size="inline" class="shrink-0 text-label-tertiary" />
              <span class="min-w-0 flex-1 truncate text-caption text-label" :title="standard.filename ?? ''">
                {{ standard.name }}
              </span>
              <span class="tnum shrink-0 text-caption text-label-tertiary">
                v{{ standard.currentVersion }}
              </span>
            </div>
          </div>
        </div>
      </DialogField>

      <DialogField
        label="插件"
        hint="可搜索或按标签筛选；已写好的组件会随执行指令交给 AI，优先复用而不是重新生成"
      >
        <p v-if="loadingPlugins" class="text-caption text-label-tertiary">正在读取插件库…</p>
        <p
          v-else-if="pluginsError"
          class="text-caption text-label-tertiary"
          :title="pluginsError"
        >
          插件库暂时不可用，不影响创建任务
        </p>
        <p v-else-if="plugins.length === 0" class="text-caption text-label-tertiary">
          还没有可用插件，可先到「插件」页添加。
        </p>
        <PluginPicker
          v-else
          v-model="selectedPluginIds"
          :plugins="plugins"
          :disabled="phase !== 'editing' || !autosaveReady"
          :max="MAX_TASK_PLUGINS"
        />
      </DialogField>

      <DialogField label="优先级" hint="数值越大越先派发，默认 0">
        <!-- 宽度包在外面：Input 的变体里有 w-full，直接给它挂 w-20 谁赢取决于 Tailwind
             产物里的顺序（实测 w-full 赢，于是优先级被拉成一整行） -->
        <div class="w-20">
          <Input
            v-model="priority"
            type="number"
            :disabled="phase !== 'editing' || !autosaveReady"
          />
        </div>
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
      <p
        v-if="draftStatusText"
        class="text-caption"
        :class="draftState === 'error' ? 'text-danger' : 'text-label-tertiary'"
        :title="draftError ?? undefined"
        role="status"
        aria-live="polite"
      >
        {{ draftStatusText }}
      </p>
    </div>

    <template #footer>
      <Button
        v-if="savedDraft && phase === 'editing'"
        variant="danger"
        :disabled="!autosaveReady"
        @click="clearDraft"
      >
        <Icon :as="Trash2" size="inline" />清除草稿
      </Button>
      <Button v-if="phase === 'editing'" @click="close">取消</Button>
      <Button v-if="phase === 'editing'" variant="primary" :disabled="!canSubmit" @click="submit">
        创建任务
      </Button>
      <Button v-else variant="primary" disabled>创建中…</Button>
    </template>
  </Dialog>
</template>
