<script setup lang="ts">
import { Copy, Eye, EyeOff, KeyRound, Pencil } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import type { EnvironmentEntry } from '@/types/api'
import { formatDateTime } from '@/utils/format'
import {
  hasInlineUrlCredentials,
  isHttpUrl,
  isMaskedEnvironmentValue,
  MASKED_ENVIRONMENT_VALUE,
} from '@/utils/environmentValue'

const props = defineProps<{
  entry: EnvironmentEntry | null
  projectName: string
}>()

const emit = defineEmits<{
  edit: [entry: EnvironmentEntry]
  copy: [entry: EnvironmentEntry]
}>()
const open = defineModel<boolean>('open', { default: false })

const revealed = ref(false)

watch([open, () => props.entry?.id], ([opened]) => {
  if (opened) revealed.value = false
})

const scopeLabel = computed(() => (props.entry?.scope === 'GLOBAL' ? '全局' : '指定项目'))
const masked = computed(() => props.entry !== null && isMaskedEnvironmentValue(props.entry))
const linkable = computed(
  () =>
    props.entry !== null &&
    isHttpUrl(props.entry.value) &&
    !hasInlineUrlCredentials(props.entry.value),
)
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="log"
    :title="entry?.description || '环境详情'"
    :description="entry?.scope === 'GLOBAL' ? '所有项目可用；项目级同键配置会覆盖它。' : '仅在指定项目执行任务时生效。'"
    :icon="KeyRound"
  >
    <template v-if="entry">
      <dl class="grid grid-cols-[88px_minmax(0,1fr)] gap-x-4 gap-y-2 text-caption">
        <dt class="text-label-secondary">环境键</dt>
        <dd class="break-all font-mono text-label-secondary">
          {{ entry.key }}（平台自动生成）
        </dd>

        <dt class="text-label-secondary">归属项目</dt>
        <dd class="flex flex-wrap items-center gap-2 text-label">
          <Badge :tone="entry.scope === 'GLOBAL' ? 'accent' : 'neutral'">
            {{ scopeLabel }}
          </Badge>
          <span v-if="entry.scope === 'PROJECT'" class="break-all">
            {{ projectName || `项目 #${entry.projectId}` }}
          </span>
        </dd>
      </dl>

      <div class="mt-4 rounded-panel border border-separator-soft bg-content p-3">
        <div class="flex items-center gap-2">
          <p class="text-caption font-emphasis text-label-secondary">环境信息</p>
          <IconButton
            v-if="masked && entry.value !== ''"
            :label="revealed ? '隐藏环境信息' : '显示环境信息'"
            size="sm"
            class="ml-auto"
            @click="revealed = !revealed"
          >
            <Icon :as="revealed ? EyeOff : Eye" size="inline" />
          </IconButton>
        </div>
        <pre
          v-if="entry.value === ''"
          class="mt-2 max-h-[28vh] overflow-y-auto whitespace-pre-wrap break-all font-mono text-caption text-label"
        >空值</pre>
        <pre
          v-else-if="masked && !revealed"
          class="mt-2 max-h-[28vh] overflow-y-auto whitespace-pre-wrap break-all font-mono text-caption text-label-tertiary"
        >{{ MASKED_ENVIRONMENT_VALUE }}</pre>
        <pre
          v-else-if="linkable"
          class="mt-2 max-h-[28vh] overflow-y-auto whitespace-pre-wrap break-all font-mono text-caption text-label"
        ><a
          :href="entry.value.trim()"
          target="_blank"
          rel="noopener noreferrer"
          class="text-accent hover:underline"
        >{{ entry.value }}</a></pre>
        <pre
          v-else
          class="mt-2 max-h-[28vh] overflow-y-auto whitespace-pre-wrap break-all font-mono text-caption text-label"
        >{{ entry.value }}</pre>
      </div>

      <div class="mt-4">
        <p class="text-caption font-emphasis text-label-secondary">标签</p>
        <div v-if="entry.tags.length > 0" class="mt-1.5 flex flex-wrap gap-1">
          <Badge v-for="tag in entry.tags" :key="tag" tone="success">{{ tag }}</Badge>
        </div>
        <p v-else class="mt-1 text-caption text-label-tertiary">未设置标签</p>
      </div>

      <dl class="mt-4 grid grid-cols-[88px_minmax(0,1fr)] gap-x-4 gap-y-1.5 border-t border-separator pt-3 text-caption">
        <dt class="text-label-secondary">创建时间</dt>
        <dd class="tnum text-label">{{ formatDateTime(entry.createdAt) }}</dd>
        <dt class="text-label-secondary">更新时间</dt>
        <dd class="tnum text-label">{{ formatDateTime(entry.updatedAt) }}</dd>
      </dl>
    </template>

    <template #footer>
      <Button @click="open = false">关闭</Button>
      <Button v-if="entry" @click="emit('copy', entry)">
        <Icon :as="Copy" size="inline" />拷贝
      </Button>
      <Button v-if="entry" variant="primary" @click="emit('edit', entry)">
        <Icon :as="Pencil" size="inline" />编辑
      </Button>
    </template>
  </Dialog>
</template>
