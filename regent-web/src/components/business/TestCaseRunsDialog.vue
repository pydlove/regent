<script setup lang="ts">
import { FileText, FlaskConical } from '@lucide/vue'
import { ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { getTestCaseRun } from '@/api/testCases'
import TestCaseRunStatusBadge from '@/components/business/TestCaseRunStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import type { TestCaseRunDetail, TestCaseRunSummary } from '@/types/api'
import { formatDateTime, formatDuration } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    runs: TestCaseRunSummary[]
    title?: string
    loading?: boolean
    error?: string | null
  }>(),
  { title: '测试执行记录', loading: false, error: null },
)
const open = defineModel<boolean>('open', { default: false })

const selectedId = ref<number | null>(null)
const detail = ref<TestCaseRunDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref<string | null>(null)

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const triggerLabel = (run: TestCaseRunSummary) =>
  run.trigger === 'MANUAL' ? '手动执行' : `任务 #${run.taskId}`

watch(open, (opened) => {
  if (!opened) return
  selectedId.value = null
  detail.value = null
  detailError.value = null
})

const selectRun = async (run: TestCaseRunSummary) => {
  selectedId.value = run.id
  detail.value = null
  detailError.value = null
  detailLoading.value = true
  try {
    detail.value = await getTestCaseRun(run.id)
  } catch (e) {
    detailError.value = messageOf(e)
  } finally {
    detailLoading.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="log"
    :title="title"
    description="记录测试进程的退出码、摘要与完整输出；历史记录不会随用例编辑而改变。"
    :icon="FlaskConical"
  >
    <div class="flex min-h-0 flex-col gap-3">
      <p v-if="loading" class="text-caption text-label-tertiary">正在读取执行记录…</p>
      <p v-else-if="error" class="text-caption text-danger">{{ error }}</p>
      <div
        v-else-if="runs.length === 0"
        class="rounded-panel border border-separator-soft bg-content px-4 py-8 text-center"
      >
        <Icon :as="FlaskConical" size="xlarge" class="mx-auto text-label-tertiary" />
        <p class="mt-2 text-body text-label-secondary">还没有执行记录</p>
        <p class="mt-1 text-caption text-label-tertiary">任务成功完成后，绑定的测试会自动出现在这里。</p>
      </div>

      <template v-else>
        <div class="scroll-area max-h-[28vh] overflow-y-auto rounded-panel border border-separator-soft bg-content">
          <button
            v-for="run in runs"
            :key="run.id"
            type="button"
            class="flex w-full items-center gap-2 border-b border-separator-soft px-3 py-2 text-left transition-colors duration-100 ease-apple last:border-b-0 hover:bg-hover"
            :class="selectedId === run.id ? 'bg-selection' : ''"
            @click="selectRun(run)"
          >
            <span class="tnum shrink-0 text-caption text-label-tertiary">
              {{ triggerLabel(run) }}
            </span>
            <span class="min-w-0 flex-1 truncate text-caption font-emphasis text-label">
              {{ run.testCaseName }}
            </span>
            <TestCaseRunStatusBadge :status="run.status" />
            <span class="tnum hidden shrink-0 text-caption text-label-tertiary sm:block">
              {{ formatDuration(run.startedAt, run.finishedAt) }}
            </span>
            <span class="tnum hidden w-28 shrink-0 text-right text-caption text-label-tertiary md:block">
              {{ formatDateTime(run.finishedAt ?? run.queuedAt) }}
            </span>
          </button>
        </div>

        <div class="min-h-24 rounded-panel border border-separator-soft bg-content p-3">
          <p v-if="detailLoading" class="text-caption text-label-tertiary">正在读取输出…</p>
          <p v-else-if="detailError" class="text-caption text-danger">{{ detailError }}</p>
          <template v-else-if="detail">
            <div class="flex flex-wrap items-center gap-2">
              <Icon :as="FileText" size="inline" class="text-label-tertiary" />
              <p class="text-caption font-emphasis text-label">执行输出</p>
              <span class="text-caption text-label-tertiary">{{ triggerLabel(detail) }}</span>
              <TestCaseRunStatusBadge :status="detail.status" />
              <span v-if="detail.exitCode !== null" class="tnum text-caption text-label-tertiary">
                exit {{ detail.exitCode }}
              </span>
              <span class="ml-auto text-caption text-label-tertiary">{{ detail.summary ?? '无摘要' }}</span>
            </div>
            <pre
              class="scroll-area mt-2 max-h-[34vh] overflow-auto whitespace-pre-wrap break-words rounded-panel bg-window px-3 py-2 font-mono text-caption leading-relaxed text-label"
            >{{ detail.output || '（测试没有产生输出）' }}</pre>
          </template>
          <p v-else class="text-caption text-label-tertiary">选择一条记录查看退出码与输出。</p>
        </div>
      </template>
    </div>

    <template #footer>
      <Button @click="open = false">关闭</Button>
    </template>
  </Dialog>
</template>
