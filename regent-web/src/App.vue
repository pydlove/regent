<script setup lang="ts">
import {
  Bug,
  Bell,
  FolderKanban,
  LayoutPanelLeft,
  ListChecks,
  PanelLeft,
  Plus,
  Settings,
  Workflow,
} from '@lucide/vue'
import { TooltipProvider } from 'reka-ui'
import { computed, onMounted, ref, watchEffect } from 'vue'
import { useRoute } from 'vue-router'

import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import ProjectFormDialog from '@/components/business/ProjectFormDialog.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Select from '@/components/ui/select/Select.vue'
import Sidebar from '@/components/ui/sidebar/Sidebar.vue'
import ToastHost from '@/components/ui/toast/ToastHost.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import Toolbar from '@/components/ui/toolbar/Toolbar.vue'
import { useOverlayScrollbar } from '@/composables/useOverlayScrollbar'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useTheme, type ThemePreference } from '@/composables/useTheme'
import { useBugStore } from '@/stores/useBugStore'
import { useDecisionStore } from '@/stores/useDecisionStore'
import { useTaskStore } from '@/stores/useTaskStore'

const { preference, setTheme } = useTheme()
const route = useRoute()
const routeTitle = computed(() => (route.meta.title as string | undefined) ?? '')

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
 * 下拉的值必须是字符串（reka-ui 的 Select 只认字符串），所以转换只在这一层做一次。
 */
const store = useTaskStore()
const decisionStore = useDecisionStore()
const bugStore = useBugStore()

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
  { label: '任务', to: '/tasks', icon: ListChecks, badge: String(store.taskTotal) },
  {
    label: '决策',
    to: '/decisions',
    icon: Workflow,
    badge: decisionStore.pendingTotal ? String(decisionStore.pendingTotal) : undefined,
  },
  {
    label: 'Bug 单',
    to: '/bugs',
    icon: Bug,
    badge: bugStore.openBugs.length ? String(bugStore.openBugs.length) : undefined,
  },
  // 项目不放角标：它是配置，不是「等你处理」的事 —— 角标一旦不表示待办，就没人再看它了
  { label: '项目', to: '/projects', icon: FolderKanban },
])

/*
 * 展示类入口固定在侧边栏最底部：样式基准页是给人查组件的参考页，不参与业务流程，
 * 也不该占着首屏 —— 默认落地在任务页。
 */
const footerNavItems = [{ label: '样式基准页', to: '/baseline', icon: LayoutPanelLeft }]

// 角标与项目下拉都挂在侧边栏/工具栏上，与具体页面无关，所以在应用启动时拉一次。
// 项目列表取不到就算了：下拉里只剩「全部项目」，比让整个应用起不来强
onMounted(() => {
  void store.loadStats()
  void store.loadProjects().catch(() => undefined)
  void decisionStore.loadPendingCount()
})

const sidebarCollapsed = ref(false)
const scroller = ref<HTMLElement | null>(null)
useOverlayScrollbar(scroller)

/*
 * 注册项目的入口在工具栏（项目下拉旁边）。弹框挂在 App 上而不是某个页面里：
 * 工具栏、项目页、以及「新建任务」里一个项目都没有时的提示都要能打开它，
 * 挂在页面上就会出现互相看不见的三份开关。
 */
const { open: projectDialogOpen, openDialog: openProjectDialog } = useProjectDialog()
</script>

<template>
  <TooltipProvider :delay-duration="400">
    <div class="flex h-screen flex-col overflow-hidden bg-window">
      <Toolbar>
        <template #leading>
          <Tooltip content="收起侧边栏">
            <IconButton
              label="收起侧边栏"
              size="lg"
              @click="sidebarCollapsed = !sidebarCollapsed"
            >
              <Icon :as="PanelLeft" size="large" />
            </IconButton>
          </Tooltip>
          <!-- 产品名唯一常驻位：品牌在工具栏最左、页面名在中间，两者不互相抢 -->
          <span class="flex shrink-0 items-center gap-1.5 pl-1.5">
            <span class="text-body font-emphasis text-label">Regent AI</span>
            <span class="text-caption text-label-secondary">任务执行平台</span>
          </span>
          <Select v-model="project" :items="projects" size="lg" class="w-44" />
          <Tooltip content="注册项目">
            <IconButton label="注册项目" size="lg" @click="openProjectDialog()">
              <Icon :as="Plus" size="large" />
            </IconButton>
          </Tooltip>
        </template>

        <span class="text-body font-emphasis text-label">{{ routeTitle }}</span>

        <template #trailing>
          <SegmentedControl v-model="themeModel" :items="themeOptions" size="lg" />
          <Tooltip content="刷新">
            <IconButton label="刷新" size="lg">
              <Icon :as="Bell" size="large" />
            </IconButton>
          </Tooltip>
          <IconButton label="设置" size="lg">
            <Icon :as="Settings" size="large" />
          </IconButton>
        </template>
      </Toolbar>

      <div class="flex min-h-0 flex-1">
        <Sidebar :items="navItems" :footer-items="footerNavItems" :collapsed="sidebarCollapsed" />

        <main ref="scroller" class="scroll-area min-w-0 flex-1 overflow-y-auto bg-content">
          <RouterView />
        </main>
      </div>
    </div>
    <ProjectFormDialog v-model:open="projectDialogOpen" />
    <ToastHost />
  </TooltipProvider>
</template>
