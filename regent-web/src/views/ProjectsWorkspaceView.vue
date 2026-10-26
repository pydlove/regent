<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import DeploymentServersView from '@/views/DeploymentServersView.vue'
import EnvironmentsView from '@/views/EnvironmentsView.vue'
import FileLibraryView from '@/views/FileLibraryView.vue'
import PluginsView from '@/views/PluginsView.vue'
import ProjectsView from '@/views/ProjectsView.vue'
import ProjectMembersView from '@/views/ProjectMembersView.vue'
import RequirementPoolView from '@/views/RequirementPoolView.vue'
import VersionsView from '@/views/VersionsView.vue'
import SectionTabs from '@/components/ui/section-tabs/SectionTabs.vue'

const route = useRoute()
const router = useRouter()
const tabComponents: Record<string, Component> = {
  projects: ProjectsView,
  versions: VersionsView,
  pool: RequirementPoolView,
  members: ProjectMembersView,
  servers: DeploymentServersView,
  environments: EnvironmentsView,
  plugins: PluginsView,
  library: FileLibraryView,
}
const current = computed(() => {
  const value = typeof route.query.view === 'string' ? route.query.view : ''
  if (value === 'delivery') return 'versions'
  return Object.hasOwn(tabComponents, value) ? value : 'projects'
})
const activeComponent = computed(() => tabComponents[current.value] ?? ProjectsView)

const selectTab = (value: string) => {
  void router.replace({
    path: '/projects',
    query: value === 'projects' ? {} : { view: value },
  })
}
</script>

<template>
  <div class="flex h-full min-h-0 min-w-0 flex-col overflow-hidden">
    <SectionTabs
      :model-value="current"
      :items="[
        { value: 'projects', label: '项目' },
        { value: 'members', label: '团队' },
        { value: 'versions', label: '版本' },
        { value: 'pool', label: '需求池' },
        { value: 'servers', label: '服务器' },
        { value: 'environments', label: '环境' },
        { value: 'plugins', label: '能力插件' },
        { value: 'library', label: '文件库' },
      ]"
      @update:model-value="selectTab"
    />
    <div class="min-h-0 min-w-0 flex-1 overflow-hidden">
      <component :is="activeComponent" />
    </div>
  </div>
</template>
