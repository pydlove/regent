<script setup lang="ts">
import { ImagePlus, Mic, Plus, Square, TriangleAlert, X } from '@lucide/vue'
import { computed, onUnmounted, ref, watch } from 'vue'

import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_BYTES, uploadTaskAttachment } from '@/api/attachments'
import { ApiError } from '@/api/http'
import { bindTaskPlugins, listPlugins } from '@/api/plugins'
import { createTask } from '@/api/tasks'
import PluginPicker from '@/components/business/PluginPicker.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Progress from '@/components/ui/progress/Progress.vue'
import Select from '@/components/ui/select/Select.vue'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useSpeechToChinese, type SpeechLanguage } from '@/composables/useSpeechToChinese'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { PluginSummary } from '@/types/api'
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
  () => projects.value.length > 1 && store.projectId === null,
)

const projectId = computed<number | null>(() => {
  if (showProjectSelect.value) return pickedProjectId.value
  if (store.projectId !== null) return store.projectId
  if (projects.value.length === 1) return projects.value[0].id
  return null
})

const fixedProject = computed(
  () => projects.value.find((project) => project.id === projectId.value) ?? null,
)

const projectHint = computed(() =>
  store.projectId !== null ? '跟随工具栏的项目筛选' : '当前只有一个项目，已自动选上',
)

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
  () => speech.supported && speech.secureContext && phase.value === 'editing',
)
const speechStatus = computed(() => {
  if (!speech.supported) return '当前浏览器不支持语音输入'
  if (!speech.secureContext) return '语音输入需要 HTTPS，或在本机 localhost 使用'
  if (speech.error.value) return speech.error.value
  if (speech.translating.value) return '正在翻译成中文…'
  if (speech.listening.value) {
    return speech.interim.value ? `正在聆听：${speech.interim.value}` : '正在聆听，说完后点击停止'
  }
  if (speech.translationFallback.value) return '翻译服务不可用，已保留识别原文'
  return speechLanguage.value === 'zh-CN' ? '中文语音直接写入指令' : '识别后自动翻译成中文'
})
const plugins = ref<PluginSummary[]>([])
const selectedPluginIds = ref<number[]>([])
const loadingPlugins = ref(false)
const pluginsError = ref<string | null>(null)

/** editing 可以改；creating 已提交，正在等创建接口返回 */
type Phase = 'editing' | 'creating'
const phase = ref<Phase>('editing')
const error = ref<string | null>(null)

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

const pending = ref<PendingImage[]>([])
let nextLocalId = 1
const dragging = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const pendingBytes = computed(() => pending.value.reduce((sum, item) => sum + item.file.size, 0))

const canSubmit = computed(
  () => phase.value === 'editing' && projectId.value !== null && prompt.value.trim().length > 0,
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
}

const loadProjects = async () => {
  loadingProjects.value = true
  try {
    await store.loadProjects()
    // 打开时就把默认项目定下来：工具栏选着的优先，只有一个项目就直接用它
    pickedProjectId.value =
      store.projectId ?? (projects.value.length === 1 ? projects.value[0].id : null)
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

// 每次打开都从头开始：上次填了一半的指令留在框里，下次打开容易被当成占位文字直接提交
watch(open, (value) => {
  if (!value) {
    speech.abort()
    return
  }
  speech.reset()
  prompt.value = ''
  priority.value = '0'
  error.value = null
  phase.value = 'editing'
  pickedProjectId.value = null
  dragging.value = false
  pluginsError.value = null
  selectedPluginIds.value = []
  releasePreviews()
  void loadProjects()
  void loadPlugins()
})

watch(speech.translationFallback, (fallback) => {
  if (fallback) {
    toast.warning(
      '语音已识别，但当前浏览器无法翻译成中文',
      '已保留识别原文；建议使用最新版 Chrome 或 Edge',
    )
  }
})

const appendSpeechText = (text: string) => {
  const normalized = text.trim()
  if (!normalized) return
  const current = prompt.value.trimEnd()
  if (!current) {
    prompt.value = normalized
    return
  }
  const needsSpace = /[A-Za-z0-9]$/.test(current) && /^[A-Za-z0-9]/.test(normalized)
  prompt.value = `${current}${needsSpace ? ' ' : ''}${normalized}`
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

const onPaste = (event: ClipboardEvent) => {
  const data = event.clipboardData
  if (!data) return
  const files = Array.from(data.items)
    .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
    .map((item) => item.getAsFile())
    .filter((file): file is File => file !== null)
  if (files.length === 0) return
  // 剪贴板里同时有文字时不动它：从网页复制一段带图的富文本，人想要的多半还是那段文字
  if (data.getData('text/plain').length > 0) return
  event.preventDefault()
  addFiles(files)
}

const onDrop = (event: DragEvent) => {
  dragging.value = false
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (files.length > 0) addFiles(files)
}

const onDragLeave = (event: DragEvent) => {
  // dragleave 在子元素之间移动时也会冒上来，只看指针真的离开了整个面板
  const next = event.relatedTarget as Node | null
  if (!next || !(event.currentTarget as Node).contains(next)) dragging.value = false
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

const close = () => {
  open.value = false
  phase.value = 'editing'
  releasePreviews()
}

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
  const images = [...pending.value]
  try {
    const task = await createTask({
      projectId: target,
      prompt: prompt.value.trim(),
      priority: Number(priority.value) || 0,
    })
    if (selectedPluginIds.value.length > 0) {
      try {
        await bindTaskPlugins(task.id, selectedPluginIds.value)
      } catch (e) {
        toast.warning(`#${task.id} 插件绑定失败`, messageOf(e))
      }
    }
    emit('created', { id: task.id, title: task.title })
    close()
    // 弹框关闭后继续用提交时的文件快照上传，不能被下一次打开时的清空逻辑截断
    if (images.length > 0) void announceUploadResult(task.id, images)
  } catch (e) {
    error.value = messageOf(e)
    phase.value = 'editing'
  }
}

onUnmounted(releasePreviews)
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="新建任务"
    description="任务先进待执行队列，派发之后 codex 才会在项目路径下跑。"
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
          block
        />
      </DialogField>
      <DialogField v-else-if="fixedProject" label="项目" :hint="projectHint">
        <div class="flex h-control items-center gap-1.5">
          <span class="text-body text-label">{{ fixedProject.name }}</span>
          <span class="text-caption text-label-tertiary">（{{ fixedProject.code }}）</span>
        </div>
      </DialogField>
      <!-- 一个项目都没有是死结：任务必须挂在项目下，所以这里给一条直达的活路，
           其余情况不在这里注册项目（入口在工具栏，见文件头注释第 5 条） -->
      <DialogField
        v-else
        label="项目"
        hint="项目决定 codex 在哪个目录跑，注册之后任务才有地方挂"
      >
        <div class="flex h-control items-center gap-2">
          <span class="text-caption text-label-tertiary">还没有注册任何项目</span>
          <Button size="sm" class="ml-auto" @click="openProjectDialog()">
            <Icon :as="Plus" size="inline" />注册项目
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
                ? '停止听写'
                : speech.translating.value
                  ? '翻译中…'
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
          v-model="prompt"
          :rows="10"
          :disabled="phase !== 'editing'"
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
            :disabled="phase !== 'editing'"
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
          :disabled="phase !== 'editing'"
          :max="MAX_TASK_PLUGINS"
        />
      </DialogField>

      <DialogField label="优先级" hint="数值越大越先派发，默认 0">
        <!-- 宽度包在外面：Input 的变体里有 w-full，直接给它挂 w-20 谁赢取决于 Tailwind
             产物里的顺序（实测 w-full 赢，于是优先级被拉成一整行） -->
        <div class="w-20">
          <Input v-model="priority" type="number" :disabled="phase !== 'editing'" />
        </div>
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button v-if="phase === 'editing'" @click="close">取消</Button>
      <Button v-if="phase === 'editing'" variant="primary" :disabled="!canSubmit" @click="submit">
        创建任务
      </Button>
      <Button v-else variant="primary" disabled>创建中…</Button>
    </template>
  </Dialog>
</template>
