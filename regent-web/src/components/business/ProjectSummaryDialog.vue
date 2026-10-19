<script setup lang="ts">
import { FileText } from '@lucide/vue'
import { computed } from 'vue'

import MarkdownContent from '@/components/business/MarkdownContent.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import type { Project } from '@/types/api'
import { formatDateTime } from '@/utils/format'

const props = defineProps<{
  project: Project | null
}>()

const open = defineModel<boolean>('open', { default: false })

const description = computed(() => {
  if (!props.project) return ''
  return `${props.project.name} · ${props.project.code}`
})
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="log"
    size="wide"
    title="项目资料"
    :description="description"
    :icon="FileText"
  >
    <template v-if="project?.summary">
      <p v-if="project.summaryStatus === 'FAILED' && project.summaryError" class="mb-3 text-caption text-danger">
        最近一次生成失败：{{ project.summaryError }}。下方保留最近成功生成的资料。
      </p>
      <MarkdownContent :markdown="project.summary" />
    </template>
    <p v-else class="text-caption text-label-tertiary">这个项目还没有可查看的项目资料。</p>

    <template #footer>
      <span v-if="project?.summaryGeneratedAt" class="mr-auto self-center text-caption text-label-tertiary">
        最近生成：{{ formatDateTime(project.summaryGeneratedAt) }}
      </span>
      <Button @click="open = false">关闭</Button>
    </template>
  </Dialog>
</template>
