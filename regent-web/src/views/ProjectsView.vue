<script setup lang="ts">
import {
  BookOpenText,
  ChevronLeft,
  ListChecks,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  X,
} from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { getProjectGitBranch, regenerateProjectSummary } from '@/api/projects'
import { listTasks } from '@/api/tasks'
import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import ProjectSummaryDialog from '@/components/business/ProjectSummaryDialog.vue'
import TaskStatusBadge from '@/components/business/TaskStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useCodexInstallDialog } from '@/composables/useCodexInstallDialog'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useToast } from '@/composables/useToast'
import { useExecutionRuntimeStore } from '@/stores/useExecutionRuntimeStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type { Project, TaskSummary } from '@/types/api'

/*
 * 项目管理。
 *
 * 这一页负责看清项目画像（路径、阈值、是否暂停）、编辑可维护字段，并查看每个项目的
 * 任务规模与最近记录。删除仍未提供独立入口。
 *
 * 列表不分页：项目是平台里数量最少的东西（个位数），分页是照猫画虎。
 */
const store = useTaskStore()
const executionRuntime = useExecutionRuntimeStore()
const router = useRouter()
const toast = useToast()
const { openDialog: openCodexInstallDialog } = useCodexInstallDialog()
const {
  openDialog: openProjectDialog,
  openEditDialog,
  lastRegistered,
  lastUpdated,
} = useProjectDialog()

const columns: TableColumn[] = [
  { key: 'name', label: '项目名称', width: '280px' },
  { key: 'code', label: '标识', width: '108px', hideBelow: 'lg' },
  { key: 'path', label: '工作区路径', width: '160px', hideBelow: '2xl' },
  { key: 'gitBranch', label: 'Git 分支', width: '128px', hideBelow: '2xl' },
  { key: 'level', label: '自动决策', width: '128px', hideBelow: 'md' },
  { key: 'status', label: '状态', width: '76px' },
  { key: 'tasks', label: '任务', align: 'right', width: '52px' },
  { key: 'actions', label: '', align: 'right', width: '44px' },
]

const loading = ref(false)
const error = ref<string | null>(null)
const keyword = ref('')

const selectedId = ref<number | null>(null)
const selected = computed(
  () => store.projects.find((project) => project.id === selectedId.value) ?? null,
)
const filteredProjects = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase('zh-CN')
  if (!query) return store.projects
  return store.projects.filter((project) =>
    [project.name, project.code, project.path, project.defaultDecisionLevel]
      .some((value) => value.toLocaleLowerCase('zh-CN').includes(query)),
  )
})
const projectCountLabel = computed(() =>
  keyword.value.trim()
    ? `${filteredProjects.value.length} / ${store.projects.length}`
    : String(store.projects.length),
)

/**
 * 每个项目下的任务总数。
 *
 * 项目是个位数，所以这里用 N 次 `size=1` 的查询换「这个项目有没有在跑东西」——
 * 后端还没有按项目聚合的统计接口，而为一个列表页加一个 SQL 聚合 + 领域投影，代价比它现在值；
 * 项目数量涨到几十个时再回头加接口，那时它才划算。
 */
const taskCounts = ref<Record<number, number>>({})

/** 选中项目的最近任务。只给选中的那一个拉列表，不给每一行拉 */
const projectTasks = ref<TaskSummary[]>([])
const projectBranches = ref<Record<number, string | null>>({})
let branchRequestSequence = 0
const summaryRegeneratingId = ref<number | null>(null)
const summaryDialogOpen = ref(false)
let summaryPollTimer: number | null = null

const summaryStatusLabel = (project: Project | null) => {
  if (!project) return ''
  return {
    NOT_GENERATED: '未生成',
    GENERATING: '生成中',
    READY: '已生成',
    FAILED: '生成失败',
  }[project.summaryStatus]
}

const stopSummaryPolling = () => {
  if (summaryPollTimer !== null) {
    window.clearTimeout(summaryPollTimer)
    summaryPollTimer = null
  }
}

const scheduleSummaryPolling = () => {
  stopSummaryPolling()
  if (!store.projects.some((project) => project.summaryStatus === 'GENERATING')) return
  summaryPollTimer = window.setTimeout(async () => {
    try {
      await store.loadProjects()
      scheduleSummaryPolling()
    } catch {
      // 下一轮由用户刷新或重新进入页面触发，不在后台无限重试。
    }
  }, 3000)
}

const loadProjectTasks = async () => {
  const id = selectedId.value
  if (id === null) {
    projectTasks.value = []
    return
  }
  try {
    projectTasks.value = (await listTasks({ projectId: id, size: 8 })).items
  } catch {
    // 这是详情里的补充信息，取不到就空着，不拿红字顶掉整个面板
    projectTasks.value = []
  }
}

const loadProjectBranches = async () => {
  const requestSequence = ++branchRequestSequence
  const projects = store.projects
  projectBranches.value = {}
  const branches = await Promise.all(
    projects.map(async (project) => {
      try {
        const result = await getProjectGitBranch(project.id)
        return [project.id, result.branchName] as const
      } catch {
        // 分支是工作区补充信息，单个项目读取失败时只把该行留空
        return [project.id, null] as const
      }
    }),
  )
  if (requestSequence === branchRequestSequence) {
    projectBranches.value = Object.fromEntries(branches)
  }
}

const load = async () => {
  loading.value = true
  error.value = null
  try {
    await store.loadProjects()
    taskCounts.value = Object.fromEntries(
      await Promise.all(
        store.projects.map(async (project) => {
          const page = await listTasks({ projectId: project.id, size: 1 })
          return [project.id, page.total] as const
        }),
      ),
    )
    // 选中项目丢了（换库、或者列表刷新后不在了）就退回第一个，别让右侧空着。
    // 手机端例外：选中即整屏详情，落地页应该是列表本身，不自动弹出
    const compact = window.matchMedia('(max-width: 1023px)').matches
    if (!compact && !store.projects.some((project) => project.id === selectedId.value)) {
      selectedId.value = store.projects[0]?.id ?? null
    }
    await Promise.all([loadProjectTasks(), loadProjectBranches()])
    scheduleSummaryPolling()
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
  } finally {
    loading.value = false
  }
}

onMounted(load)
onBeforeUnmount(stopSummaryPolling)
watch(selectedId, () => {
  summaryDialogOpen.value = false
  void loadProjectTasks()
})

/** 注册成功后：整页刷新，并把新项目选中 —— 刚建的东西应该立刻是焦点 */
watch(lastRegistered, async (project) => {
  if (!project) return
  keyword.value = ''
  await load()
  selectedId.value = project.id
})

/** 编辑成功后重新加载分支等运行时信息，并保持当前项目选中。 */
watch(lastUpdated, async (project) => {
  if (!project) return
  keyword.value = ''
  await load()
  selectedId.value = project.id
})

watch(keyword, () => {
  if (selectedId.value !== null && !filteredProjects.value.some((project) => project.id === selectedId.value)) {
    selectedId.value = null
  }
})

/** 跳到任务页并带上这个项目的筛选：双击项目行或点检查器按钮走同一条路 */
const openInTasks = (project = selected.value) => {
  if (!project) return
  selectedId.value = project.id
  store.projectId = project.id
  void router.push('/tasks')
}

const regenerateSummary = async () => {
  if (!selected.value || selected.value.summaryStatus === 'GENERATING') return
  if (!executionRuntime.status) await executionRuntime.check()
  if (executionRuntime.status && !executionRuntime.status.available) {
    openCodexInstallDialog()
    toast.warning(
      'Codex 未就绪，无法生成项目资料',
      '请先到「设置 → 执行」安装或配置 Codex，再重新生成',
    )
    return
  }
  const project = selected.value
  summaryRegeneratingId.value = project.id
  try {
    await regenerateProjectSummary(project.id)
    await store.loadProjects()
    scheduleSummaryPolling()
    toast.success(`「${project.name}」开始生成项目资料`, 'Codex 以只读方式分析工作区，不会改动文件')
  } catch (e) {
    toast.failure(
      '无法启动项目资料生成',
      e instanceof ApiError ? e.message : '请确认后端与 Codex 可用',
    )
  } finally {
    summaryRegeneratingId.value = null
  }
}
</script>

<template>
  <div class="flex h-full min-h-0">
    <div class="flex min-w-0 flex-1 flex-col">
      <ListToolbar>
        <template #title>
          <h1 class="text-body font-emphasis text-label">项目</h1>
          <span class="tnum text-body text-label-tertiary">{{ projectCountLabel }}</span>
        </template>
        <template #filters>
          <div class="relative min-w-0 flex-1 sm:w-52 sm:flex-none">
            <Icon
              :as="Search"
              size="inline"
              class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
            />
            <Input
              v-model="keyword"
              size="lg"
              placeholder="搜索项目名称、标识或路径"
              aria-label="搜索项目"
              class="pl-7 pr-7"
            />
            <button
              v-if="keyword"
              type="button"
              class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
              aria-label="清空项目搜索"
              @click="keyword = ''"
            >
              <Icon :as="X" size="inline" />
            </button>
          </div>
        </template>
        <template #actions>
          <Tooltip content="刷新">
            <IconButton label="刷新项目" size="lg" :disabled="loading" @click="load">
              <Icon :as="RefreshCw" size="large" />
            </IconButton>
          </Tooltip>
          <Button variant="primary" size="lg" @click="openProjectDialog()">
            <Icon :as="Plus" size="inline" />注册项目
          </Button>
        </template>
      </ListToolbar>

      <div class="scroll-area min-h-0 flex-1 overflow-auto">
        <Table
          :columns="columns"
          :rows="filteredProjects"
          :selected-id="selectedId"
          :show-empty="false"
          @select="selectedId = $event.id"
          @row-dblclick="openInTasks"
        >
          <template #cell="{ row, column }">
            <span
              v-if="column.key === 'name'"
              class="block truncate font-emphasis text-label"
              :title="String(row.name)"
            >
              {{ row.name }}
            </span>
            <span
              v-else-if="column.key === 'gitBranch'"
              class="font-mono text-caption text-label-secondary group-data-[selected=true]:text-white"
              :title="projectBranches[row.id as number] || undefined"
            >
              {{ projectBranches[row.id as number] || '—' }}
            </span>
            <DecisionLevelBadge v-else-if="column.key === 'level'" :level="row.defaultDecisionLevel" />
            <StatusDot
              v-else-if="column.key === 'status'"
              :dot="row.enabled ? 'bg-success' : 'bg-neutral'"
            >
              {{ row.enabled ? '启用' : '已暂停' }}
            </StatusDot>
            <span
              v-else-if="column.key === 'tasks'"
              class="text-label-secondary group-data-[selected=true]:text-white"
            >
              {{ taskCounts[row.id as number] ?? '—' }}
            </span>
            <Tooltip v-else-if="column.key === 'actions'" content="编辑项目">
              <IconButton
                label="编辑项目"
                size="sm"
                @click.stop="openEditDialog(row as Project)"
              >
                <Icon :as="Pencil" size="inline" />
              </IconButton>
            </Tooltip>
          </template>
        </Table>

        <EmptyState
          v-if="loading && store.projects.length === 0"
          title="正在加载…"
          description="从后端读取项目列表"
        />
        <EmptyState v-else-if="error" title="加载失败" :description="error" />
        <EmptyState
          v-else-if="store.projects.length === 0"
          title="还没有注册任何项目"
          description="项目决定 codex 在哪个目录跑。注册一个，任务才有地方挂 —— 路径必须是本机已存在的目录。"
        />
        <EmptyState
          v-else-if="filteredProjects.length === 0"
          title="没有匹配的项目"
          description="换个名称、标识或路径关键词再试。"
        >
          <Button @click="keyword = ''">清除搜索</Button>
        </EmptyState>
      </div>
    </div>

    <!-- 与任务页同一套：桌面右栏，手机整屏覆盖层（390px 放不下并排两栏） -->
    <aside
      :class="[
        'bg-window',
        selected
          ? 'fixed inset-0 z-40 w-full overflow-y-auto border-separator p-4 lg:static lg:inset-auto lg:z-auto lg:w-72 lg:shrink-0 lg:border-l'
          : 'hidden lg:block lg:w-72 lg:shrink-0 lg:overflow-y-auto lg:border-l lg:border-separator lg:p-4',
      ]"
    >
      <template v-if="selected">
        <button
          type="button"
          class="mb-3 inline-flex items-center gap-0.5 text-caption text-accent lg:hidden"
          @click="selectedId = null"
        >
          <Icon :as="ChevronLeft" size="inline" />返回列表
        </button>
        <div class="flex items-center gap-2">
          <span class="text-caption text-label-tertiary">{{ selected.code }}</span>
          <StatusDot
            class="ml-auto"
            :dot="selected.enabled ? 'bg-success' : 'bg-neutral'"
          >
            {{ selected.enabled ? '启用' : '已暂停' }}
          </StatusDot>
        </div>
        <div class="mt-2 flex items-center gap-2">
          <h2 class="min-w-0 flex-1 truncate text-body font-emphasis text-label">
            {{ selected.name }}
          </h2>
          <Button size="sm" @click="openEditDialog(selected)">
            <Icon :as="Pencil" size="inline" />编辑
          </Button>
        </div>

        <p class="mt-3 text-caption text-label-secondary">工作区路径</p>
        <p class="mt-1 break-all rounded-panel border border-separator-soft bg-content px-2 py-1.5 font-mono text-caption text-label">
          {{ selected.path }}
        </p>

        <dl class="mt-4 space-y-2">
          <div class="flex items-center gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">自动决策</dt>
            <dd class="ml-auto"><DecisionLevelBadge :level="selected.defaultDecisionLevel" /></dd>
          </div>
          <div v-if="projectBranches[selected.id]" class="flex items-start gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">Git 分支</dt>
            <dd class="min-w-0 break-all text-right font-mono text-caption text-label">
              {{ projectBranches[selected.id] }}
            </dd>
          </div>
          <div class="flex items-center gap-2">
            <dt class="whitespace-nowrap text-caption text-label-secondary">任务总数</dt>
            <dd class="tnum ml-auto text-caption text-label">
              {{ taskCounts[selected.id] ?? '—' }}
            </dd>
          </div>
        </dl>

        <p class="mt-3 text-caption text-label-tertiary">
          生效等级高于「{{ selected.defaultDecisionLevel }}」的决策一律升级人工。暂停的项目不再派发新任务，
          但录入不受影响。
        </p>

        <div class="mt-4 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <p class="text-caption font-emphasis text-label-secondary">项目资料</p>
            <StatusDot
              class="ml-auto"
              :dot="selected.summaryStatus === 'GENERATING'
                ? 'bg-accent'
                : selected.summaryStatus === 'READY'
                  ? 'bg-success'
                  : selected.summaryStatus === 'FAILED'
                    ? 'bg-danger'
                    : 'bg-neutral'"
            >
              {{ summaryStatusLabel(selected) }}
            </StatusDot>
          </div>

          <p v-if="selected.summaryStatus === 'GENERATING'" class="mt-2 text-caption text-label-tertiary">
            Codex 正在只读分析工作区，完成后会自动刷新。
          </p>
          <p v-if="selected.summaryStatus === 'FAILED'" class="mt-2 text-caption text-danger">
            {{ selected.summaryError || '项目资料生成失败' }}
          </p>
          <p
            v-if="!selected.summary && selected.summaryStatus === 'NOT_GENERATED'"
            class="mt-2 text-caption text-label-tertiary"
          >
            尚未生成项目资料。Codex 会读项目结构与说明，只生成总结，不改文件。
          </p>
          <p
            v-if="selected.summaryGeneratedAt"
            class="mt-2 text-caption text-label-tertiary"
          >
            最近生成：{{ new Date(selected.summaryGeneratedAt).toLocaleString('zh-CN') }}
          </p>
          <div class="mt-3 flex gap-2">
            <Button
              v-if="selected.summary"
              class="flex-1"
              size="sm"
              @click="summaryDialogOpen = true"
            >
              <Icon :as="BookOpenText" size="inline" />查看资料
            </Button>
            <Button
              :class="selected.summary ? 'flex-1' : 'w-full'"
              size="sm"
              :disabled="
                selected.summaryStatus === 'GENERATING'
                  || summaryRegeneratingId === selected.id
              "
              @click="regenerateSummary"
            >
              <Icon :as="RefreshCw" size="inline" />
              {{
                selected.summaryStatus === 'GENERATING'
                  ? '生成中'
                  : selected.summaryStatus === 'READY'
                    ? '重新生成'
                    : '生成资料'
              }}
            </Button>
          </div>
        </div>

        <div class="mt-4 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <p class="text-caption font-emphasis text-label-secondary">该项目的任务</p>
            <span v-if="projectTasks.length > 0" class="tnum text-caption text-label-tertiary">
              {{ projectTasks.length }}
            </span>
          </div>

          <p v-if="projectTasks.length === 0" class="mt-2 text-caption text-label-tertiary">
            还没有任务。任务页右上角的「+」可以给它录一条。
          </p>
          <ul v-else class="mt-2 space-y-1">
            <li
              v-for="task in projectTasks"
              :key="task.id"
              class="rounded-panel border border-separator-soft bg-content px-2.5 py-1.5"
            >
              <span class="flex items-center gap-2">
                <span class="tnum text-caption text-label-tertiary">#{{ task.id }}</span>
                <TaskStatusBadge :status="task.status" class="ml-auto" />
              </span>
              <span class="mt-0.5 block truncate text-caption text-label-secondary">
                {{ task.title }}
              </span>
            </li>
          </ul>

          <Button class="mt-3" size="sm" block @click="openInTasks()">
            <Icon :as="ListChecks" size="inline" />在任务页查看
          </Button>
        </div>
      </template>

      <p v-else class="text-caption text-label-tertiary">选中一个项目查看详情</p>
    </aside>

    <ProjectSummaryDialog v-model:open="summaryDialogOpen" :project="selected" />
  </div>
</template>
