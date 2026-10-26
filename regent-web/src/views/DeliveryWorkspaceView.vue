<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import DeploymentsView from '@/views/DeploymentsView.vue'
import TestCasesView from '@/views/TestCasesView.vue'
import GitCommitsView from '@/views/GitCommitsView.vue'
import SectionTabs from '@/components/ui/section-tabs/SectionTabs.vue'

const route = useRoute()
const router = useRouter()
const tabComponents: Record<string, Component> = {
  deployments: DeploymentsView,
  tests: TestCasesView,
  'git-commits': GitCommitsView,
}
const current = computed(() => {
  const value = typeof route.query.view === 'string' ? route.query.view : ''
  return Object.hasOwn(tabComponents, value) ? value : 'deployments'
})
const activeComponent = computed(() => tabComponents[current.value] ?? DeploymentsView)

const selectTab = (value: string) => {
  void router.replace({
    path: '/delivery',
    query: value === 'deployments' ? {} : { view: value },
  })
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <SectionTabs
      :model-value="current"
      :items="[
        { value: 'deployments', label: '部署' },
        { value: 'tests', label: '测试' },
    { value: 'git-commits', label: 'Git 提交' },
      ]"
      @update:model-value="selectTab"
    />
    <div class="min-h-0 flex-1">
      <component :is="activeComponent" />
    </div>
  </div>
</template>
