<script setup lang="ts">
import { ImagePlus, Play, Plus, RefreshCw, RotateCcw, XCircle } from '@lucide/vue'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import { ACCEPTED_IMAGE_TYPES, listTaskAttachments, uploadTaskAttachment } from '@/api/attachments'
import { getDecision } from '@/api/decisions'
import { ApiError } from '@/api/http'
import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import DecisionReviewDialog from '@/components/business/DecisionReviewDialog.vue'
import NewTaskDialog from '@/components/business/NewTaskDialog.vue'
import RunDetailDialog from '@/components/business/RunDetailDialog.vue'
import RunStatusBadge from '@/components/business/RunStatusBadge.vue'
import TaskAttachmentGrid from '@/components/business/TaskAttachmentGrid.vue'
import TaskAttachmentsDialog from '@/components/business/TaskAttachmentsDialog.vue'
import TaskStatusBadge from '@/components/business/TaskStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import Pagination from '@/components/ui/pagination/Pagination.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useDecisionStore } from '@/stores/useDecisionStore'
import { useRunStore } from '@/stores/useRunStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type { DecisionSummary, TaskAttachment } from '@/types/api'
import { canCancel, canDispatch, canReject, canResume, canRetry, type TaskStatus } from '@/types/task'
import { formatDuration, formatTokens } from '@/utils/format'

/*
 * 视图不判断状态。按钮能不能点一律问 types/task.ts 里的守卫，
 * 守卫用的又是状态机本身 —— 状态机改了不用回来改模板。
 */

const store = useTaskStore()
const decisionStore = useDecisionStore()
const runStore = useRunStore()
const toast = useToast()

const columns: TableColumn[] = [
  { key: 'id', label: '#', width: '56px', numeric: true },
  { key: 'title', label: '任务' },
  { key: 'level', label: '等级', width: '128px' },
  { key: 'status', label: '状态', width: '104px' },
  { key: 'duration', label: '耗时', align: 'right', width: '72px' },
  { key: 'tokens', label: 'Token', align: 'right', width: '80px' },
]

const filterOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'BLOCKED', label: '待批准' },
  { value: 'DONE', label: '已完成' },
]

// SegmentedControl 的 model 是 string，这里换算成 TaskStatus —— 免得把字符串漏进 store
const filter = computed({
  get: () => store.statusFilter as string,
  set: (value: string) => {
    store.statusFilter = value as TaskStatus | 'ALL'
  },
})

/*
 * 搜索框加 250ms 防抖。后端分页之后每敲一个字就是一次请求，
 * 不做防抖的话「登录」两个字会打出五次查询，而前四次的结果都会被立刻覆盖掉。
 */
const keywordInput = ref(store.keyword)
let keywordTimer: number | undefined
watch(keywordInput, (value) => {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(() => {
    store.keyword = value
  }, 250)
})

const selected = computed(() => store.selected)

/** 当前选中任务正阻塞在哪个决策上。列表项只有决策单号，问题与建议要单独取。 */
const decision = ref<DecisionSummary | null>(null)
watch(
  () => store.selected?.decisionId,
  async (id) => {
    decision.value = id ? await getDecision(id).catch(() => null) : null
  },
  { immediate: true },
)

const reviewOpen = ref(false)
const reviewMode = ref<'approve' | 'reject'>('approve')

onMounted(() => {
  void store.load()
  void store.loadStats()
})

const openReview = (mode: 'approve' | 'reject') => {
  reviewMode.value = mode
  reviewOpen.value = true
}

const onReviewed = async (payload: { mode: 'approve' | 'reject'; note: string }) => {
  const current = decision.value
  const task = selected.value
  if (!current || !task) return

  const failure = await decisionStore.review(current.id, payload.mode, payload.note)
  if (failure) {
    toast.warning(`决策 #${current.id} 没能${payload.mode === 'approve' ? '批准' : '驳回'}`, failure)
    return
  }

  // 决议会改任务状态，所以列表、统计、右侧详情、决策卡四处都要重拉 ——
  // 少刷一处就会出现「列表说执行中、侧栏说待批准」这种自相矛盾的首屏
  await Promise.all([store.load(), store.loadStats()])
  const fresh = store.items.find((row) => row.id === task.id)
  if (fresh) await store.select(fresh)
  decision.value = await getDecision(current.id).catch(() => null)

  if (payload.mode === 'approve') {
    toast.success(`#${task.id} 已批准`, '执行器从断点续跑，上下文里带着你的决定')
  } else {
    toast.show(`#${task.id} 已驳回，任务终止`, { description: '理由已写入决策留痕' })
  }
}

const retry = async () => {
  const task = selected.value
  if (!task) return
  const failure = await store.retry(task.id)
  if (failure) {
    toast.warning(`#${task.id} 重试被拒绝`, failure)
    return
  }
  toast.show(`#${task.id} 已回到待执行`, { description: '执行器下一轮会重新派发' })
}

const dispatch = async () => {
  const task = selected.value
  if (!task) return
  const failure = await store.dispatch(task.id)
  if (failure) {
    toast.warning(`#${task.id} 派发被拒绝`, failure)
    return
  }
  // 认领成功只说明「执行器已经起跑」，结果还在路上，所以随后把运行记录刷出来
  await runStore.load(task.id)
  toast.show(`#${task.id} 已派发`, { description: '执行器在后台跑，运行记录每 3 秒自动刷新' })
}

const cancel = async () => {
  const task = selected.value
  if (!task) return
  const failure = await store.cancel(task.id)
  if (failure) {
    toast.warning(`#${task.id} 终止被拒绝`, failure)
    return
  }
  toast.show(`#${task.id} 已终止`, { description: '任务不会再有后续动作' })
}

/*
 * 新建任务。建完之后要把列表翻回「能看到它」的位置：列表按优先级与 ID 降序，
 * 新任务一定在第一页第一行，但筛选条件或页码会把它挡在外面 —— 那看起来就像创建失败了。
 */
const createOpen = ref(false)

const onTaskCreated = async (payload: { id: number; title: string }) => {
  // 筛选条件全归位：带着「已完成」「某个项目」的筛选去看刚建的任务，它必然不在结果里，
  // 而「新建成功却看不到」在用户眼里就是创建失败
  store.statusFilter = 'ALL'
  store.projectId = null
  store.keyword = ''
  keywordInput.value = ''
  store.page = 1
  // 上面几个赋值会顺带触发 store 的 watch（同一份查询），但这里要等的是「请求已完成」，
  // 所以显式再拉一次：多一次本地 GET，换的是「创建成功立刻能看到并选中」这件确定的事
  await store.load()
  await store.loadStats()
  const fresh = store.items.find((row) => row.id === payload.id)
  if (fresh) await store.select(fresh)
  // 弹框是先建任务再传图，这里按 0 张读一次；传完它会再发 uploaded 事件让我们重拉
  await loadAttachments(payload.id)
  toast.success(`#${payload.id} 已创建`, '在待执行队列里，点「派发执行」才会真的跑')
}

/*
 * 附件。跟着选中行走，切一行重拉一次。
 *
 * 放在视图里而不是任务 store 里：只有这一个地方用它，进了 store 反而要跟着列表翻页、
 * 筛选一起失效重算，而那几处跟附件没关系。
 */
const attachments = ref<TaskAttachment[]>([])
const viewerOpen = ref(false)
const viewerId = ref<number | null>(null)

const loadAttachments = async (id: number) => {
  try {
    attachments.value = await listTaskAttachments(id)
  } catch {
    // 附件是附加信息：取不到就当作没有，不拿一条红字把整个详情面板顶掉
    attachments.value = []
  }
}

const openViewer = (attachment: TaskAttachment) => {
  viewerId.value = attachment.id
  viewerOpen.value = true
}

/** 补图。上传接口挂在任务下，所以这里对任何状态的任务都成立 —— 图片在「下次」执行开局才读 */
const attachInput = ref<HTMLInputElement | null>(null)
const attaching = ref(false)

const attachFiles = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  // 清掉 value：连着选同一个文件两次时 change 不会再触发，看起来像「点了没反应」
  input.value = ''
  const task = selected.value
  if (!task || files.length === 0) return

  attaching.value = true
  let uploaded = 0
  for (const file of files) {
    try {
      await uploadTaskAttachment(task.id, file)
      uploaded += 1
    } catch (e) {
      toast.warning(
        `「${file.name}」没传上去`,
        e instanceof ApiError ? e.message : '无法连接后端，请确认服务已启动',
      )
    }
  }
  attaching.value = false
  if (uploaded > 0) {
    await loadAttachments(task.id)
    toast.success(`${uploaded} 张图已附上`, `#${task.id} 下次执行时会一并交给 codex`)
  }
}

/** 弹框传完图（含失败后重试成功）时刷新当前选中行的缩略图 */
const onUploaded = (payload: { id: number }) => {
  if (store.selected?.id === payload.id) void loadAttachments(payload.id)
}

/*
 * 运行记录跟着选中行走：切一行重拉一次，同时把上一条的弹框关掉 ——
 * 弹框里显示的是「某一次尝试」，换了任务还留着它，只会让人对错上下文。
 */
let runsTimer: number | undefined
const stopRunsTimer = () => {
  window.clearInterval(runsTimer)
  runsTimer = undefined
}

const refreshRuns = async () => {
  const task = selected.value
  if (task) await runStore.load(task.id)
}

const runDialogOpen = ref(false)
const openRun = async (runId: number) => {
  runDialogOpen.value = true
  await runStore.openRun(runId)
}

watch(
  () => store.selected?.id,
  (id) => {
    stopRunsTimer()
    runDialogOpen.value = false
    runStore.reset()
    // 看图弹框里显示的是「某一张图」，换了任务还留着它，只会让人对错上下文
    viewerOpen.value = false
    viewerId.value = null
    attachments.value = []
    if (id !== undefined) {
      void runStore.load(id)
      void loadAttachments(id)
    }
  },
  { immediate: true },
)

// 只在「有记录还在跑」时开表：没在跑的时候还每 3 秒打一次后端，只是白耗电
watch(
  () => runStore.hasRunning,
  (running) => {
    stopRunsTimer()
    if (running) runsTimer = window.setInterval(() => void runStore.refreshQuietly(), 3000)
  },
)
onUnmounted(stopRunsTimer)
</script>

<template>
  <div class="flex h-full min-h-0">
    <div class="flex min-w-0 flex-1 flex-col">
      <div class="flex h-toolbar shrink-0 items-center gap-3 border-b border-separator px-5">
        <h1 class="text-body font-emphasis text-label">任务</h1>
        <span class="tnum text-body text-label-tertiary">
          {{ store.total }} / {{ store.taskTotal }}
        </span>
        <SegmentedControl v-model="filter" :items="filterOptions" size="lg" class="ml-2" />
        <div class="ml-auto flex items-center gap-2">
          <Input v-model="keywordInput" placeholder="搜索" size="lg" class="w-52" />
          <Tooltip content="新建任务">
            <IconButton label="新建任务" size="lg" @click="createOpen = true">
              <Icon :as="Plus" size="large" />
            </IconButton>
          </Tooltip>
        </div>
      </div>

      <div class="scroll-area min-h-0 flex-1 overflow-auto">
        <Table
          :columns="columns"
          :rows="store.items"
          :selected-id="selected?.id ?? null"
          :show-empty="false"
          @select="store.select"
        >
          <template #cell="{ row, column }">
            <DecisionLevelBadge v-if="column.key === 'level'" :level="row.level" />
            <TaskStatusBadge v-else-if="column.key === 'status'" :status="row.status" />
            <span v-else-if="column.key === 'duration'" class="tnum text-label-secondary">
              {{ formatDuration(row.startedAt, row.finishedAt) }}
            </span>
            <span v-else-if="column.key === 'tokens'" class="tnum text-label-secondary">
              {{ formatTokens(row.tokens) }}
            </span>
          </template>
        </Table>

        <EmptyState
          v-if="store.loading && store.items.length === 0"
          title="正在加载…"
          description="从后端读取任务列表"
        />
        <EmptyState
          v-else-if="store.error"
          title="加载失败"
          :description="store.error"
        />
        <EmptyState
          v-else-if="store.total === 0"
          title="没有匹配的任务"
          description="换个关键词，或把筛选切回「全部」"
        />
      </div>

      <Pagination v-model:page="store.page" v-model:page-size="store.pageSize" :total="store.total" />
    </div>

    <aside class="w-72 shrink-0 overflow-y-auto border-l border-separator bg-window p-4">
      <template v-if="selected">
        <div class="flex items-center gap-2">
          <span class="tnum text-caption text-label-tertiary">#{{ selected.id }}</span>
          <DecisionLevelBadge :level="selected.level" />
          <TaskStatusBadge :status="selected.status" class="ml-auto" />
        </div>
        <h2 class="mt-2 text-body font-emphasis text-label">{{ selected.title }}</h2>

        <p v-if="store.detail" class="mt-2 text-caption text-label-secondary">
          {{ store.detail.prompt }}
        </p>

        <dl class="mt-4 space-y-2">
          <div class="flex items-center gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">项目</dt>
            <dd class="ml-auto text-caption text-label">{{ selected.projectName }}</dd>
          </div>
          <div class="flex items-center gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">分支</dt>
            <dd class="ml-auto text-caption text-label">{{ selected.branchName ?? '—' }}</dd>
          </div>
          <div class="flex items-center gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">耗时</dt>
            <dd class="tnum ml-auto text-caption text-label">
              {{ formatDuration(selected.startedAt, selected.finishedAt) }}
            </dd>
          </div>
          <div class="flex items-center gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">Token</dt>
            <dd class="tnum ml-auto text-caption text-label">{{ formatTokens(selected.tokens) }}</dd>
          </div>
          <div class="flex items-center gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">尝试次数</dt>
            <dd class="tnum ml-auto text-caption text-label">{{ selected.attemptCount }}</dd>
          </div>
          <div v-if="store.detail?.threadId" class="flex items-center gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">执行线程</dt>
            <dd class="tnum ml-auto truncate text-caption text-label">
              {{ store.detail.threadId }}
            </dd>
          </div>
        </dl>

        <!-- 图片不只是留档：派发时它们会跟着指令一起交给 codex，所以放在详情里而不是另开一页 -->
        <div class="mt-4 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <p class="text-caption font-emphasis text-label-secondary">图片</p>
            <span v-if="attachments.length > 0" class="tnum text-caption text-label-tertiary">
              {{ attachments.length }}
            </span>
            <Tooltip content="加一张图">
              <IconButton
                label="添加图片"
                size="sm"
                class="ml-auto"
                :disabled="attaching"
                @click="attachInput?.click()"
              >
                <Icon :as="ImagePlus" size="regular" />
              </IconButton>
            </Tooltip>
          </div>

          <TaskAttachmentGrid
            v-if="attachments.length > 0"
            :attachments="attachments"
            class="mt-2"
            @select="openViewer"
          />
          <p v-else class="mt-2 text-caption text-label-tertiary">
            还没有图片。有些事说不清 —— 截图和设计稿在派发时会一起交给 codex。
          </p>

          <input
            ref="attachInput"
            type="file"
            :accept="ACCEPTED_IMAGE_TYPES.join(',')"
            multiple
            class="hidden"
            @change="attachFiles"
          />
        </div>

        <div
          v-if="decision"
          class="mt-4 rounded-panel border border-separator-soft bg-content p-2.5"
        >
          <p class="text-caption font-emphasis text-label-secondary">等待你的决定</p>
          <p class="mt-1 text-caption text-label">{{ decision.question }}</p>
          <p v-if="decision.matchedRuleName" class="mt-1 text-caption text-label-tertiary">
            命中规则「{{ decision.matchedRuleName }}」
            <template v-if="decision.levelEscalated">
              · AI 自评 {{ decision.aiLevel }}，已升级到 {{ decision.effectiveLevel }}
            </template>
          </p>
        </div>

        <div class="mt-4 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <p class="text-caption font-emphasis text-label-secondary">运行记录</p>
            <span v-if="runStore.runs.length > 0" class="tnum text-caption text-label-tertiary">
              {{ runStore.runs.length }}
            </span>
            <Tooltip content="刷新">
              <IconButton
                label="刷新运行记录"
                size="sm"
                class="ml-auto"
                @click="refreshRuns"
              >
                <Icon :as="RefreshCw" size="regular" />
              </IconButton>
            </Tooltip>
          </div>

          <p v-if="runStore.error" class="mt-2 text-caption text-danger">{{ runStore.error }}</p>
          <p
            v-else-if="runStore.loading && runStore.runs.length === 0"
            class="mt-2 text-caption text-label-tertiary"
          >
            正在加载…
          </p>
          <p v-else-if="runStore.runs.length === 0" class="mt-2 text-caption text-label-tertiary">
            还没有跑过。
            <template v-if="canDispatch(selected.status)">点下面的「派发执行」让它跑起来。</template>
          </p>
          <ul v-else class="mt-2 space-y-1">
            <li v-for="run in runStore.runs" :key="run.id">
              <!-- 整行可点：一次尝试的详情（事件流、出参原文）在弹框里，288px 装不下日志 -->
              <button
                type="button"
                class="w-full rounded-panel border border-separator-soft bg-content px-2.5 py-1.5 text-left transition-colors duration-100 ease-apple hover:bg-hover"
                @click="openRun(run.id)"
              >
                <span class="flex items-center gap-2">
                  <span class="tnum text-caption text-label-tertiary">#{{ run.attemptNo }}</span>
                  <RunStatusBadge :status="run.status" />
                  <span class="tnum ml-auto text-caption text-label-tertiary">
                    {{ formatTokens(run.tokens) }}
                  </span>
                </span>
                <span class="mt-0.5 flex items-center gap-2">
                  <span class="truncate text-caption text-label-secondary">
                    {{ run.summary ?? '（没有摘要）' }}
                  </span>
                  <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">
                    {{ formatDuration(run.startedAt, run.finishedAt) }}
                  </span>
                </span>
              </button>
            </li>
          </ul>
          <p v-if="runStore.hasRunning" class="mt-1.5 text-caption text-label-tertiary">
            执行中 · 每 3 秒自动刷新
          </p>
        </div>

        <div class="mt-4 flex flex-wrap items-center gap-2">
          <Button
            v-if="canResume(selected.status)"
            variant="primary"
            size="sm"
            @click="openReview('approve')"
          >
            <Icon :as="Play" size="inline" />批准并续跑
          </Button>
          <Button v-if="canDispatch(selected.status)" variant="primary" size="sm" @click="dispatch">
            <Icon :as="Play" size="inline" />派发执行
          </Button>
          <Button v-if="canReject(selected.status)" size="sm" @click="openReview('reject')">
            <Icon :as="XCircle" size="inline" />驳回
          </Button>
          <Button v-if="canRetry(selected.status)" size="sm" @click="retry">
            <Icon :as="RotateCcw" size="inline" />重试
          </Button>
          <Button v-if="canCancel(selected.status)" size="sm" @click="cancel">终止</Button>
          <Button v-if="runStore.runs.length > 0" size="sm" @click="openRun(runStore.runs[0].id)">
            打开日志
          </Button>
        </div>
      </template>

      <p v-else class="text-caption text-label-tertiary">选中一行查看详情</p>
    </aside>

    <NewTaskDialog v-model:open="createOpen" @created="onTaskCreated" @uploaded="onUploaded" />

    <TaskAttachmentsDialog
      v-model:open="viewerOpen"
      v-model:selected-id="viewerId"
      :attachments="attachments"
    />

    <DecisionReviewDialog
      v-model:open="reviewOpen"
      :decision="decision"
      :mode="reviewMode"
      @done="onReviewed"
    />
    <RunDetailDialog
      v-model:open="runDialogOpen"
      :detail="runStore.detail"
      :loading="runStore.detailLoading"
      :error="runStore.detailError"
    />
  </div>
</template>
