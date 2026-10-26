<script setup lang="ts">
import {
  Building2,
  CircleHelp,
  ChevronLeft,
  ChevronRight,
  FolderKanban,
  LayoutDashboard,
  ListChecks,
  PanelLeftOpen,
  Plus,
  RefreshCw,
  ServerCog,
  Settings,
  Smartphone,
  TriangleAlert,
  Workflow,
} from '@lucide/vue'
import { TooltipProvider } from 'reka-ui'
import { computed, onMounted, onUnmounted, ref, watch, watchEffect } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import AppFooter from '@/components/business/AppFooter.vue'
import CodexInstallDialog from '@/components/business/CodexInstallDialog.vue'
import FailedTaskNotifications from '@/components/business/FailedTaskNotifications.vue'
import GlobalSearch from '@/components/business/GlobalSearch.vue'
import ProjectFormDialog from '@/components/business/ProjectFormDialog.vue'
import UserProfileCard from '@/components/business/UserProfileCard.vue'
import UserProfileDialog from '@/components/business/UserProfileDialog.vue'
import { buttonVariants } from '@/components/ui/button/button.variants'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import SearchableSelect from '@/components/ui/searchable-select/SearchableSelect.vue'
import Sidebar from '@/components/ui/sidebar/Sidebar.vue'
import ToastHost from '@/components/ui/toast/ToastHost.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import Toolbar from '@/components/ui/toolbar/Toolbar.vue'
import { useOverlayScrollbar } from '@/composables/useOverlayScrollbar'
import { useCodexInstallDialog } from '@/composables/useCodexInstallDialog'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useTheme, type ThemePreference } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/useAuthStore'
import { useDecisionStore } from '@/stores/useDecisionStore'
import { useExecutionRuntimeStore } from '@/stores/useExecutionRuntimeStore'
import { useOrganizationStore } from '@/stores/useOrganizationStore'
import { useTaskStore } from '@/stores/useTaskStore'
import { useWebUpdate } from '@/composables/useWebUpdate'

const { preference, setTheme } = useTheme()
const route = useRoute()
const router = useRouter()
const routeTitle = computed(() => (route.meta.title as string | undefined) ?? '')
const authPage = computed(() => route.meta.layout === 'auth')

// 标签页标题统一带上产品名：切页是「任务 · Regent AI 任务执行平台」，而不是永远一个笼统的 Regent
watchEffect(() => {
  document.title = routeTitle.value
    ? `${routeTitle.value} · Regent AI 任务执行平台`
    : 'Regent AI 任务执行平台'
})

const themeModel = computed({
  get: () => preference.value,
  set: (value: string) => setTheme(value as ThemePreference),
})

const themeOptions = [
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
  { value: 'system', label: '跟随系统' },
]

/*
 * 项目下拉。选项与选中值都在任务 store 里：筛选是列表查询的一个条件（后端按 project_id 过滤），
 * 不是本组件自己的临时状态 —— 放在这里，换页、搜索、切项目走的是同一条「改条件 → 回第一页 → 重拉」的路。
 *
 * Combobox 的值必须是字符串，所以转换只在这一层做一次。
 */
const store = useTaskStore()
const organizationStore = useOrganizationStore()
const decisionStore = useDecisionStore()
const auth = useAuthStore()
const {
  latest: latestWebRelease,
  hasUpdate: hasWebUpdate,
  dismiss: dismissWebUpdate,
} = useWebUpdate()
const reloadPage = () => window.location.reload()
const executionRuntime = useExecutionRuntimeStore()
const { openDialog: openCodexInstallDialog } = useCodexInstallDialog()
const projectContext = computed(() => route.path === '/projects')
const organizationProjects = computed(() => {
  const organizationId = organizationStore.currentId
  return store.projects.filter(
    (item) => organizationId !== null && item.organizationId === organizationId,
  )
})

const project = computed({
  get: () => {
    if (store.projectId !== null) return String(store.projectId)
    return projectContext.value ? '' : 'all'
  },
  set: (value: string | undefined) => {
    // 项目列表尚未加载时，下拉没有匹配项会瞬时发出空值；此时保留持久化选择，
    // 等 loadProjects 拿到列表后再校验并恢复。
    if (store.projects.length === 0) return
    // 万一拿到空值或非数字，一律退回「全部项目」：把 NaN 塞进查询串会变成一个 400，
    // 而用户看到的是「列表怎么报错了」，跟他的操作对不上
    if (value === 'all') {
      if (!projectContext.value) store.projectId = null
      return
    }
    const parsed = Number(value)
    store.projectId = value && Number.isFinite(parsed) ? parsed : null
  },
})

const projects = computed(() => {
  return [
    ...(projectContext.value
      ? []
      : [{ value: 'all', label: '本组织全部项目' }]),
    ...organizationProjects.value
      .map((item) => ({
        value: String(item.id),
        // 暂停的项目留在列表里但标出来：它还在、只是不派发新任务，藏起来反而让人以为项目不见了
        label: item.enabled ? item.name : `${item.name}（已暂停）`,
      })),
  ]
})

watch(
  [
    projectContext,
    () => organizationProjects.value.map((project) => project.id).join(','),
  ],
  () => {
    if (!projectContext.value || organizationProjects.value.length === 0) return
    if (organizationProjects.value.some((item) => item.id === store.projectId)) return
    store.projectId = organizationProjects.value[0]!.id
  },
  { immediate: true },
)

const organization = computed({
  get: () => (organizationStore.currentId === null
    ? 'none'
    : String(organizationStore.currentId)),
  set: (value: string | undefined) => {
    const parsed = Number(value)
    if (value && value !== 'none' && Number.isFinite(parsed)) {
      organizationStore.select(parsed)
    }
  },
})

const organizationOptions = computed(() => [
  ...organizationStore.organizations.map((item) => ({
    value: String(item.id),
    label: item.name,
  })),
  ...(organizationStore.organizations.length === 0
    ? [{
        value: 'none',
        label: organizationStore.loading
          ? '正在加载组织…'
          : organizationStore.error
            ? '组织加载失败'
            : '尚未创建组织',
      }]
    : []),
])

const organizationMenu = computed(() => [
  ...((organizationStore.organizations.length === 0
    || organizationStore.canAccessManagement)
    ? [{ label: '组织管理', to: '/organizations', icon: Building2 }]
    : []),
  ...(organizationStore.canManageOrganization
    ? [{ label: '研发流程', to: '/organizations/workflow', icon: Workflow }]
    : []),
])

// 一级菜单按用户工作流组织；组织相关能力收拢在“组织”下作为二级菜单。
const navGroups = computed(() => [
  {
    label: '工作区',
    items: [
      { label: '工作台', to: '/dashboard', icon: LayoutDashboard },
      {
        label: '任务',
        to: '/tasks',
        icon: ListChecks,
        badge: store.taskTotal,
        badgeLabel: `共 ${store.taskTotal} 个任务`,
      },
      { label: '交付', to: '/delivery', icon: ServerCog },
      { label: '项目', to: '/projects', icon: FolderKanban },
      ...(organizationMenu.value.length > 0
        ? [{
            label: '组织',
            icon: Building2,
            children: organizationMenu.value,
          }]
        : []),
    ],
  },
])

const profileDialogOpen = ref(false)

// 登录页不请求业务数据；登录成功并切换到管理界面后再拉角标与项目下拉。
watch(
  () => auth.isAuthenticated,
  (authenticated) => {
    if (!authenticated) {
      executionRuntime.clear()
      store.clearProjectScope()
      organizationStore.clear()
      return
    }
    void store.loadStats()
    void organizationStore.load()
      .then(() => store.setOrganizationScope(organizationStore.currentId))
      .catch(() => {
        // 中继或后端短暂不可用时保留上一次的项目范围，不能把“加载失败”表现成“没有项目”。
        if (organizationStore.organizations.length === 0) store.clearProjectScope()
      })
    void decisionStore.loadPendingCount()
    executionRuntime.startMonitoring()
  },
  { immediate: true },
)

watch(
  () => organizationStore.currentId,
  (organizationId) => {
    if (!auth.isAuthenticated) return
    if (store.organizationId === organizationId) return
    void store.setOrganizationScope(organizationId)
  },
)

watch(
  () => auth.user?.mustChangePassword,
  (mustChange, previous) => {
    if (previous === true && mustChange === false && auth.isAuthenticated) {
      void organizationStore.load()
        .then(() => store.setOrganizationScope(organizationStore.currentId))
        .catch(() => {
          if (organizationStore.organizations.length === 0) store.clearProjectScope()
        })
      void decisionStore.loadPendingCount()
    }
  },
)

/*
 * 第一次进入且 Codex 缺失时，直接打开安装向导。
 * sessionStorage 按用户记录“本次浏览器会话已提示过”，避免刷新页面时反复弹框；
 * 关闭后顶部常驻提示条仍然保留，用户随时可以重新打开。
 */
watch(
  [() => auth.isAuthenticated, () => executionRuntime.unavailable, () => auth.user?.id],
  ([authenticated, unavailable, userId]) => {
    if (!authenticated || !unavailable || !userId) return
    const key = `regent.codex-setup-prompted.${userId}`
    try {
      if (sessionStorage.getItem(key) === '1') return
      sessionStorage.setItem(key, '1')
    } catch {
      // 浏览器禁用存储时仍然弹一次；只是刷新后可能再次出现。
    }
    openCodexInstallDialog()
  },
  { immediate: true },
)

const handleUnauthorized = () => {
  auth.clear()
  if (authPage.value) return
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
}

let lastUserSyncAt = 0
const syncCurrentUser = () => {
  if (!auth.isAuthenticated || document.visibilityState === 'hidden') return
  if (organizationStore.error) retryOrganizationLoad()
  const now = Date.now()
  if (now - lastUserSyncAt < 15_000) return
  lastUserSyncAt = now
  void auth.refreshCurrentUser().then((active) => {
    if (!active && !authPage.value) handleUnauthorized()
  })
}
const syncCurrentUserWhenVisible = () => {
  if (document.visibilityState === 'visible') syncCurrentUser()
}
let userSyncTimer: number | null = null

/**
 * 组织列表是项目、团队和研发流程的入口。
 *
 * 公网中继在服务重启时会短暂超时，失败后必须允许用户原地重试；
 * 不能要求用户关闭客户端或刷新整个页面，更不能把失败清空成“尚未创建组织”。
 */
function retryOrganizationLoad() {
  if (!auth.isAuthenticated || organizationStore.loading) return
  void organizationStore.load()
    .then(() => {
      if (
        store.organizationId !== organizationStore.currentId
        || store.projects.length === 0
      ) {
        return store.setOrganizationScope(organizationStore.currentId)
      }
    })
    .catch(() => {
      if (organizationStore.organizations.length === 0) store.clearProjectScope()
    })
}

const compactViewport = ref(window.matchMedia('(max-width: 1023px)').matches)
const sidebarCollapsed = ref(compactViewport.value)
const sidebarToggleLabel = computed(() =>
  sidebarCollapsed.value ? '展开侧边栏' : '收起侧边栏',
)
const scroller = ref<HTMLElement | null>(null)
useOverlayScrollbar(scroller)

const syncViewport = () => {
  const nextCompact = window.matchMedia('(max-width: 1023px)').matches
  if (nextCompact && !compactViewport.value) sidebarCollapsed.value = true
  compactViewport.value = nextCompact
}

onMounted(() => {
  window.addEventListener('resize', syncViewport)
  window.addEventListener('focus', syncCurrentUser)
  window.addEventListener('online', retryOrganizationLoad)
  window.addEventListener('regent:unauthorized', handleUnauthorized)
  document.addEventListener('visibilitychange', syncCurrentUserWhenVisible)
  userSyncTimer = window.setInterval(syncCurrentUser, 60_000)
})
onUnmounted(() => {
  window.removeEventListener('resize', syncViewport)
  window.removeEventListener('focus', syncCurrentUser)
  window.removeEventListener('online', retryOrganizationLoad)
  window.removeEventListener('regent:unauthorized', handleUnauthorized)
  document.removeEventListener('visibilitychange', syncCurrentUserWhenVisible)
  if (userSyncTimer !== null) window.clearInterval(userSyncTimer)
  executionRuntime.stopMonitoring()
})

// 手机端点导航后自动收起抽屉：否则点了「决策」还要再点一次遮罩才能看到内容
watch(
  () => route.fullPath,
  () => {
    if (scroller.value) scroller.value.scrollLeft = 0
    if (compactViewport.value) sidebarCollapsed.value = true
  },
)

/*
 * 新建项目的入口在工具栏（项目下拉旁边）。弹框挂在 App 上而不是某个页面里：
 * 工具栏、项目页、以及「新建任务」里一个项目都没有时的提示都要能打开它，
 * 挂在页面上就会出现互相看不见的三份开关。
 */
const { open: projectDialogOpen, openDialog: openProjectDialog } = useProjectDialog()
</script>

<template>
  <TooltipProvider :delay-duration="400">
    <div v-if="!authPage" class="flex h-screen flex-col overflow-hidden bg-window">
      <Toolbar>
        <template #leading>
          <div class="lg:hidden">
            <Tooltip content="展开侧边栏">
              <IconButton
                label="展开侧边栏"
                size="lg"
                :aria-expanded="!sidebarCollapsed"
                @click="sidebarCollapsed = !sidebarCollapsed"
              >
                <Icon :as="PanelLeftOpen" size="large" />
              </IconButton>
            </Tooltip>
          </div>
          <!-- 产品名唯一常驻位：品牌在工具栏最左、页面名在中间，两者不互相抢 -->
          <span class="flex shrink-0 items-center gap-2">
            <span class="sr-only">Regent AI 任务执行平台</span>
            <span
              aria-hidden="true"
              class="relative grid size-7 shrink-0 place-items-center rounded-[7px] bg-label text-content shadow-control"
            >
              <span class="translate-y-px text-[14px] font-semibold leading-none">R</span>
              <span
                class="absolute bottom-[3px] right-[3px] size-1.5 rounded-full bg-accent ring-2 ring-label"
              />
            </span>
            <span aria-hidden="true" class="hidden flex-col lg:flex">
              <span class="text-[14px] font-emphasis leading-[15px] text-label">Regent AI</span>
              <span class="mt-0.5 text-[10px] font-medium leading-[12px] text-label-secondary">
                任务执行平台
              </span>
            </span>
          </span>
          <div class="hidden sm:block">
            <SearchableSelect
              v-model="organization"
              :items="organizationOptions"
              aria-label="搜索或选择组织"
              empty-text="没有匹配的组织"
              size="lg"
              class="w-44"
            />
          </div>
          <div class="hidden sm:block">
            <SearchableSelect
              v-model="project"
              :items="projects"
              aria-label="搜索或选择项目"
              empty-text="没有匹配的项目"
              size="lg"
              class="w-44"
            />
          </div>
          <div v-if="organizationStore.canManageProjects" class="hidden sm:block">
            <Tooltip content="新建项目">
              <IconButton label="新建项目" size="lg" @click="openProjectDialog()">
                <Icon :as="Plus" size="large" />
              </IconButton>
            </Tooltip>
          </div>
        </template>

        <span class="text-body font-emphasis text-label">{{ routeTitle }}</span>

        <template #trailing>
          <div class="hidden lg:block">
            <SegmentedControl v-model="themeModel" :items="themeOptions" size="lg" />
          </div>
          <GlobalSearch />
          <FailedTaskNotifications />
          <div class="hidden lg:block">
            <Tooltip content="下载手机 App">
              <RouterLink
                to="/download/app"
                aria-label="下载手机 App"
                title="下载手机 App"
                :class="[buttonVariants({ variant: 'ghost', size: 'lg' }), 'w-control-lg px-0']"
              >
                <Icon :as="Smartphone" size="large" />
              </RouterLink>
            </Tooltip>
          </div>
          <Tooltip content="使用说明">
            <RouterLink
              to="/help"
              aria-label="使用说明"
              title="使用说明"
              :class="[buttonVariants({ variant: 'ghost', size: 'lg' }), 'w-control-lg px-0']"
            >
              <Icon :as="CircleHelp" size="large" />
            </RouterLink>
          </Tooltip>
          <Tooltip content="设置">
            <RouterLink
              to="/settings"
              aria-label="设置"
              title="设置"
              :class="[
                buttonVariants({ variant: 'ghost', size: 'lg' }),
                'w-control-lg px-0',
                route.path.startsWith('/settings') ? 'bg-accent/10 text-accent' : '',
              ]"
            >
              <Icon :as="Settings" size="large" />
            </RouterLink>
          </Tooltip>
        </template>
      </Toolbar>

      <div
        v-if="hasWebUpdate"
        role="status"
        class="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-accent/25 bg-accent/8 px-4 py-2 text-caption text-label"
      >
        <Icon :as="RefreshCw" size="inline" class="shrink-0 text-accent" />
        <span class="min-w-0 flex-1">
          工作台有新版本
          <span class="tnum font-emphasis">{{ latestWebRelease?.version }}</span>，刷新页面即可使用。
        </span>
        <button
          type="button"
          class="font-emphasis text-accent underline underline-offset-2"
          @click="reloadPage"
        >
          立即刷新
        </button>
        <button
          type="button"
          class="text-label-secondary hover:text-label"
          @click="dismissWebUpdate"
        >
          稍后
        </button>
      </div>

      <div
        v-if="auth.isAuthenticated && organizationStore.error"
        role="alert"
        class="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-danger/20 bg-danger/6 px-4 py-2 text-caption text-danger"
      >
        <Icon :as="TriangleAlert" size="inline" class="shrink-0" />
        <span class="min-w-0 flex-1">
          {{
            organizationStore.organizations.length === 0
              ? '组织与项目加载失败，当前无法进入团队和研发流程。'
              : '组织列表刷新失败，当前显示上一次成功加载的数据。'
          }}
          <span class="text-label-secondary">（{{ organizationStore.error }}）</span>
        </span>
        <button
          type="button"
          class="font-emphasis underline underline-offset-2 disabled:opacity-50"
          :disabled="organizationStore.loading"
          @click="retryOrganizationLoad"
        >
          {{ organizationStore.loading ? '重试中…' : '重试' }}
        </button>
      </div>

      <div
        v-if="executionRuntime.unavailable"
        role="status"
        class="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-warning/25 bg-warning/10 px-4 py-2 text-caption text-warning"
      >
        <Icon :as="TriangleAlert" size="inline" class="shrink-0" />
        <span class="min-w-0 flex-1">
          Codex 未就绪。可以继续录入和浏览，但任务派发与项目摘要生成需要先安装 Codex。
        </span>
        <button
          type="button"
          class="font-emphasis underline underline-offset-2"
          @click="openCodexInstallDialog"
        >
          安装 Codex
        </button>
        <button
          type="button"
          class="font-emphasis underline underline-offset-2 disabled:opacity-50"
          :disabled="executionRuntime.loading"
          @click="executionRuntime.check(true)"
        >
          {{ executionRuntime.loading ? '检测中…' : '重新检测' }}
        </button>
      </div>

      <div class="flex min-h-0 flex-1">
        <!-- 手机端抽屉的遮罩：点空白处收起侧边栏 -->
        <div
          v-if="compactViewport && !sidebarCollapsed"
          class="fixed inset-0 z-20 bg-black/25 lg:hidden"
          @click="sidebarCollapsed = true"
        />
        <div class="relative flex shrink-0">
          <Sidebar
            id="app-sidebar"
            :groups="navGroups"
            :collapsed="sidebarCollapsed"
          >
            <div v-if="auth.user" class="mt-auto">
              <UserProfileCard
                :user="auth.user"
                @edit="profileDialogOpen = true"
              />
            </div>
          </Sidebar>
          <!-- 折叠开关骑在侧边栏右边框的垂直中心；收起后停在内容区左缘，保证还能展开 -->
          <div
            class="absolute top-1/2 z-20 hidden -translate-y-1/2 transition-[left,transform] duration-200 ease-apple lg:block"
            :class="sidebarCollapsed ? 'left-0 translate-x-0' : 'left-sidebar -translate-x-1/2'"
          >
            <Tooltip :content="sidebarToggleLabel" side="right">
              <IconButton
                :label="sidebarToggleLabel"
                size="sm"
                variant="default"
                aria-controls="app-sidebar"
                :aria-expanded="!sidebarCollapsed"
                @click="sidebarCollapsed = !sidebarCollapsed"
              >
                <Icon
                  :as="sidebarCollapsed ? ChevronRight : ChevronLeft"
                  size="inline"
                />
              </IconButton>
            </Tooltip>
          </div>
        </div>

        <main ref="scroller" class="scroll-area min-w-0 flex-1 overflow-x-hidden overflow-y-auto bg-content">
          <RouterView />
        </main>
      </div>
      <AppFooter />
    </div>
    <RouterView v-else />
    <ProjectFormDialog v-if="!authPage" v-model:open="projectDialogOpen" />
    <CodexInstallDialog v-if="!authPage" />
    <UserProfileDialog v-if="!authPage" v-model:open="profileDialogOpen" />
    <ToastHost />
  </TooltipProvider>
</template>
