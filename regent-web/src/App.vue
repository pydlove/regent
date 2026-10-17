<script setup lang="ts">
import {
  FolderKanban,
  KeyRound,
  LayoutDashboard,
  LayoutPanelLeft,
  ListChecks,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Settings,
  Workflow,
} from '@lucide/vue'
import { TooltipProvider } from 'reka-ui'
import { computed, onMounted, onUnmounted, ref, watch, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import FailedTaskNotifications from '@/components/business/FailedTaskNotifications.vue'
import ProjectFormDialog from '@/components/business/ProjectFormDialog.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import SearchableSelect from '@/components/ui/searchable-select/SearchableSelect.vue'
import Sidebar from '@/components/ui/sidebar/Sidebar.vue'
import ToastHost from '@/components/ui/toast/ToastHost.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import Toolbar from '@/components/ui/toolbar/Toolbar.vue'
import { useOverlayScrollbar } from '@/composables/useOverlayScrollbar'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useTheme, type ThemePreference } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/useAuthStore'
import { useDecisionStore } from '@/stores/useDecisionStore'
import { useTaskStore } from '@/stores/useTaskStore'

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
const decisionStore = useDecisionStore()
const auth = useAuthStore()

const project = computed({
  get: () => (store.projectId === null ? 'all' : String(store.projectId)),
  set: (value: string | undefined) => {
    // 万一拿到空值或非数字，一律退回「全部项目」：把 NaN 塞进查询串会变成一个 400，
    // 而用户看到的是「列表怎么报错了」，跟他的操作对不上
    const parsed = Number(value)
    store.projectId = value && value !== 'all' && Number.isFinite(parsed) ? parsed : null
  },
})

const projects = computed(() => [
  { value: 'all', label: '全部项目' },
  ...store.projects.map((item) => ({
    value: String(item.id),
    // 暂停的项目留在列表里但标出来：它还在、只是不派发新任务，藏起来反而让人以为项目不见了
    label: item.enabled ? item.name : `${item.name}（已暂停）`,
  })),
])

// 角标只挂「需要人管」的数：待批决策的数字和决策页是同一个源，不会两处对不上
const navItems = computed(() => [
  { label: '工作台', to: '/dashboard', icon: LayoutDashboard },
  { label: '任务', to: '/tasks', icon: ListChecks, badge: String(store.taskTotal) },
  {
    label: '决策',
    to: '/decisions',
    icon: Workflow,
    badge: decisionStore.pendingTotal ? String(decisionStore.pendingTotal) : undefined,
  },
  // 项目不放角标：它是配置，不是「等你处理」的事 —— 角标一旦不表示待办，就没人再看它了
  { label: '项目', to: '/projects', icon: FolderKanban },
  { label: '环境', to: '/environments', icon: KeyRound },
])

/*
 * 设置与样式基准页固定在侧边栏最底部：它们都不参与业务流转，也不该占着首屏。
 */
const footerNavItems = [
  { label: '设置', to: '/settings', icon: Settings },
  { label: '样式基准页', to: '/baseline', icon: LayoutPanelLeft },
]

// 登录页不请求业务数据；登录成功并切换到管理界面后再拉角标与项目下拉。
watch(
  () => auth.isAuthenticated,
  (authenticated) => {
    if (!authenticated) return
    void store.loadStats()
    void store.loadProjects().catch(() => undefined)
    void decisionStore.loadPendingCount()
  },
  { immediate: true },
)

const logout = async () => {
  await auth.logout()
  await router.push({ name: 'login' })
}

const handleUnauthorized = () => {
  auth.clear()
  if (authPage.value) return
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
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
  window.addEventListener('regent:unauthorized', handleUnauthorized)
})
onUnmounted(() => {
  window.removeEventListener('resize', syncViewport)
  window.removeEventListener('regent:unauthorized', handleUnauthorized)
})

// 手机端点导航后自动收起抽屉：否则点了「决策」还要再点一次遮罩才能看到内容
watch(
  () => route.path,
  () => {
    if (compactViewport.value) sidebarCollapsed.value = true
  },
)

/*
 * 注册项目的入口在工具栏（项目下拉旁边）。弹框挂在 App 上而不是某个页面里：
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
              <span class="flex items-center gap-1">
                <span class="text-[14px] font-emphasis leading-[15px] text-label">Regent</span>
                <span
                  class="rounded-[3px] bg-accent px-1.5 text-[10px] font-emphasis leading-[12px] text-white"
                >
                  AI
                </span>
              </span>
              <span class="mt-0.5 text-[10px] font-medium leading-[12px] text-label-secondary">
                任务执行平台
              </span>
            </span>
          </span>
          <!-- 桌面端把折叠开关贴在品牌右侧；手机端抽屉入口仍留在左上角 -->
          <div class="hidden lg:block">
            <Tooltip :content="sidebarToggleLabel">
              <IconButton
                :label="sidebarToggleLabel"
                size="lg"
                :aria-expanded="!sidebarCollapsed"
                @click="sidebarCollapsed = !sidebarCollapsed"
              >
                <Icon
                  :as="sidebarCollapsed ? PanelLeftOpen : PanelLeftClose"
                  size="large"
                />
              </IconButton>
            </Tooltip>
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
          <div class="hidden sm:block">
            <Tooltip content="注册项目">
              <IconButton label="注册项目" size="lg" @click="openProjectDialog()">
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
          <FailedTaskNotifications />
          <Tooltip :content="auth.user ? `退出登录（${auth.user.email ?? auth.user.username}）` : '退出登录'">
            <IconButton label="退出登录" size="lg" @click="logout">
              <Icon :as="LogOut" size="large" />
            </IconButton>
          </Tooltip>
          <Tooltip content="设置">
            <IconButton label="设置" size="lg" @click="router.push('/settings')">
              <Icon :as="Settings" size="large" />
            </IconButton>
          </Tooltip>
        </template>
      </Toolbar>

      <div class="flex min-h-0 flex-1">
        <!-- 手机端抽屉的遮罩：点空白处收起侧边栏 -->
        <div
          v-if="compactViewport && !sidebarCollapsed"
          class="fixed inset-0 z-20 bg-black/25 lg:hidden"
          @click="sidebarCollapsed = true"
        />
        <Sidebar :items="navItems" :footer-items="footerNavItems" :collapsed="sidebarCollapsed" />

        <main ref="scroller" class="scroll-area min-w-0 flex-1 overflow-y-auto bg-content">
          <RouterView />
        </main>
      </div>
    </div>
    <RouterView v-else />
    <ProjectFormDialog v-if="!authPage" v-model:open="projectDialogOpen" />
    <ToastHost />
  </TooltipProvider>
</template>
