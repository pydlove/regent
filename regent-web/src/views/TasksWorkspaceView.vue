<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import PlansView from '@/views/PlansView.vue'
import RequirementsView from '@/views/RequirementsView.vue'
import TasksView from '@/views/TasksView.vue'
import SectionTabs from '@/components/ui/section-tabs/SectionTabs.vue'

const route = useRoute()
const router = useRouter()
const tabComponents: Record<string, Component> = {
  tasks: TasksView,
  plans: PlansView,
  requirements: RequirementsView,
}
const current = computed(() => {
  const value = typeof route.query.view === 'string' ? route.query.view : ''
  return Object.hasOwn(tabComponents, value) ? value : 'tasks'
})
const activeComponent = computed(() => tabComponents[current.value] ?? TasksView)

const selectTab = (value: string) => {
  void router.replace({
    path: '/tasks',
    query: value === 'tasks' ? {} : { view: value },
  })
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <SectionTabs
      :model-value="current"
      :items="[
        { value: 'tasks', label: '任务' },
        { value: 'plans', label: '计划' },
        { value: 'requirements', label: '需求' },
      ]"
      @update:model-value="selectTab"
    />
    <div class="min-h-0 flex-1">
      <component :is="activeComponent" />
    </div>
  </div>
</template>
