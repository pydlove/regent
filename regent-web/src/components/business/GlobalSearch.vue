<script setup lang="ts">
import {
  CircleHelp,
  FileText,
  FolderKanban,
  LayoutDashboard,
  ListChecks,
  Search,
  ServerCog,
  Settings,
  Workflow,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { listTasks } from '@/api/tasks'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import Popover from '@/components/ui/popover/Popover.vue'
import { useTaskStore } from '@/stores/useTaskStore'

const store = useTaskStore()
const router = useRouter()
const open = ref(false)
const query = ref('')
const taskResults = ref<Array<{ id: number; title: string; projectName: string }>>([])
const searching = ref(false)
let timer: number | undefined
let sequence = 0

const pages = [
  { title: '工作台', description: '执行控制塔与待办', to: '/dashboard', icon: LayoutDashboard },
  { title: '任务', description: '全部任务', to: '/tasks', icon: ListChecks },
  { title: '需求', description: '需求澄清与任务拆解', to: '/tasks?view=requirements', icon: FileText },
  { title: '计划', description: '批量计划', to: '/tasks?view=plans', icon: Workflow },
  { title: '交付', description: '部署与测试', to: '/delivery', icon: ServerCog },
  { title: '项目', description: '项目与执行资源', to: '/projects', icon: FolderKanban },
  { title: '设置', description: '账户、执行设备与系统设置', to: '/settings', icon: Settings },
  { title: '使用说明', description: '功能说明与排障', to: '/help', icon: CircleHelp },
]

const pageResults = computed(() => {
  const value = query.value.trim().toLocaleLowerCase('zh-CN')
  if (!value) return pages.slice(0, 5)
  return pages.filter((page) =>
    `${page.title} ${page.description}`.toLocaleLowerCase('zh-CN').includes(value),
  )
})
const projectResults = computed(() => {
  const value = query.value.trim().toLocaleLowerCase('zh-CN')
  return store.projects
    .filter((project) => !value || `${project.name} ${project.code}`.toLocaleLowerCase('zh-CN').includes(value))
    .slice(0, 5)
})
const hasResults = computed(() =>
  pageResults.value.length > 0 || projectResults.value.length > 0 || taskResults.value.length > 0,
)

const searchTasks = async () => {
  const value = query.value.trim()
  const current = ++sequence
  if (!value) {
    taskResults.value = []
    searching.value = false
    return
  }
  searching.value = true
  try {
    const result = await listTasks({
      projectId: store.projectId,
      keyword: value,
      page: 1,
      size: 5,
    })
    if (current === sequence) {
      taskResults.value = result.items.map((task) => ({
        id: task.id,
        title: task.title,
        projectName: task.projectName,
      }))
    }
  } catch {
    if (current === sequence) taskResults.value = []
  } finally {
    if (current === sequence) searching.value = false
  }
}

watch(query, () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => void searchTasks(), 250)
})

const close = async (location: string) => {
  open.value = false
  query.value = ''
  taskResults.value = []
  await router.push(location)
}
</script>

<template>
  <Popover v-model:open="open" align="end">
    <template #trigger>
      <IconButton label="全局搜索" size="lg">
        <Icon :as="Search" size="large" />
      </IconButton>
    </template>

    <section class="w-[min(92vw,420px)]" aria-label="全局搜索">
      <div class="relative">
        <Icon
          :as="Search"
          size="inline"
          class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
        />
        <Input
          v-model="query"
          size="lg"
          class="pl-7"
          placeholder="搜索功能、项目或任务"
          autofocus
        />
      </div>

      <div class="mt-2 max-h-[60vh] overflow-y-auto">
        <template v-if="pageResults.length > 0">
          <p class="px-2 py-1 text-caption font-emphasis text-label-tertiary">功能</p>
          <button
            v-for="page in pageResults"
            :key="page.to"
            type="button"
            class="flex w-full items-center gap-2 rounded-control px-2 py-2 text-left hover:bg-hover"
            @click="close(page.to)"
          >
            <Icon :as="page.icon" size="regular" class="text-label-secondary" />
            <span class="min-w-0">
              <span class="block text-caption font-emphasis text-label">{{ page.title }}</span>
              <span class="block truncate text-caption text-label-tertiary">{{ page.description }}</span>
            </span>
          </button>
        </template>

        <template v-if="projectResults.length > 0">
          <p class="px-2 py-1 text-caption font-emphasis text-label-tertiary">项目</p>
          <button
            v-for="project in projectResults"
            :key="project.id"
            type="button"
            class="flex w-full items-center gap-2 rounded-control px-2 py-2 text-left hover:bg-hover"
            @click="close('/projects')"
          >
            <Icon :as="FolderKanban" size="regular" class="text-label-secondary" />
            <span class="min-w-0 flex-1">
              <span class="block truncate text-caption font-emphasis text-label">{{ project.name }}</span>
              <span class="block truncate text-caption text-label-tertiary">{{ project.code }}</span>
            </span>
          </button>
        </template>

        <template v-if="taskResults.length > 0">
          <p class="px-2 py-1 text-caption font-emphasis text-label-tertiary">任务</p>
          <button
            v-for="task in taskResults"
            :key="task.id"
            type="button"
            class="flex w-full items-center gap-2 rounded-control px-2 py-2 text-left hover:bg-hover"
            @click="close(`/tasks?taskId=${task.id}`)"
          >
            <Icon :as="ListChecks" size="regular" class="text-label-secondary" />
            <span class="min-w-0 flex-1">
              <span class="block truncate text-caption font-emphasis text-label">{{ task.title }}</span>
              <span class="block truncate text-caption text-label-tertiary">
                #{{ task.id }} · {{ task.projectName }}
              </span>
            </span>
          </button>
        </template>

        <p v-if="searching" class="px-2 py-3 text-center text-caption text-label-tertiary">正在搜索任务…</p>
        <p v-else-if="!hasResults" class="px-2 py-5 text-center text-caption text-label-tertiary">
          没有匹配结果
        </p>
      </div>
    </section>
  </Popover>
</template>
