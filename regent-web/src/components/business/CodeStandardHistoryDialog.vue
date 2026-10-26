<script setup lang="ts">
import { FileClock, FileUp } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import {
  listCodeStandardVersions,
  uploadCodeStandardVersion,
} from '@/api/codeStandards'
import { ApiError } from '@/api/http'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import { useToast } from '@/composables/useToast'
import type { CodeStandard, CodeStandardVersion } from '@/types/api'
import { formatBytes, formatDateTime } from '@/utils/format'

const props = defineProps<{ standard: CodeStandard | null }>()
const emit = defineEmits<{ changed: [] }>()
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const versions = ref<CodeStandardVersion[]>([])
const loading = ref(false)
const uploading = ref(false)
const error = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const latestId = computed(() => versions.value[0]?.id ?? null)

watch(open, async (opened) => {
  if (!opened || !props.standard) return
  await load()
})

const load = async () => {
  if (!props.standard) return
  loading.value = true
  error.value = null
  try {
    versions.value = await listCodeStandardVersions(props.standard.id)
  } catch (e) {
    versions.value = []
    error.value = e instanceof ApiError ? e.message : '版本记录读取失败'
  } finally {
    loading.value = false
  }
}

const upload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !props.standard) return
  uploading.value = true
  error.value = null
  try {
    await uploadCodeStandardVersion(props.standard.id, file)
    await load()
    emit('changed')
    toast.success('开发规范已生成新版本', '已有任务继续使用创建时的版本快照')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '新版本上传失败'
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    size="large"
    :title="standard ? `${standard.name} · 版本记录` : '版本记录'"
    description="每个版本不可变。更新规范只影响之后创建的任务。"
  >
    <div class="flex min-h-64 flex-col gap-3">
      <div class="flex items-center gap-2">
        <Badge>{{ versions.length }} 个版本</Badge>
        <Button class="ml-auto" size="sm" :disabled="uploading" @click="fileInput?.click()">
          <Icon :as="FileUp" size="inline" />
          {{ uploading ? '上传中…' : '上传新版本' }}
        </Button>
        <input
          ref="fileInput"
          type="file"
          accept=".md,.txt,.yaml,.yml,.json,.xml"
          class="hidden"
          @change="upload"
        />
      </div>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
      <p v-else-if="loading" class="text-caption text-label-tertiary">正在读取版本记录…</p>
      <EmptyState
        v-else-if="versions.length === 0"
        title="还没有版本"
        description="上传规范文件后会生成 v1。"
      />
      <div v-else class="scroll-area max-h-96 space-y-1.5 overflow-y-auto">
        <a
          v-for="version in versions"
          :key="version.id"
          :href="version.contentUrl"
          class="flex min-h-12 items-center gap-3 rounded-panel border border-separator-soft bg-content px-3 py-2 hover:border-accent/50"
        >
          <Icon :as="FileClock" size="regular" class="shrink-0 text-label-tertiary" />
          <span class="min-w-0 flex-1">
            <span class="flex items-center gap-2">
              <span class="truncate text-caption font-emphasis text-label">{{ version.filename }}</span>
              <Badge v-if="version.id === latestId" tone="accent">当前</Badge>
            </span>
            <span class="mt-0.5 block text-caption text-label-tertiary">
              v{{ version.versionNo }} · {{ formatBytes(version.sizeBytes) }} ·
              {{ formatDateTime(version.createdAt) }}
            </span>
          </span>
        </a>
      </div>
    </div>

    <template #footer>
      <Button @click="open = false">关闭</Button>
    </template>
  </Dialog>
</template>
