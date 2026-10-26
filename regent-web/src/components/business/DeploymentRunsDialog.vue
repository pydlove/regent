<script setup lang="ts">
import {
  AlertTriangle,
  ArrowDown,
  FileText,
  ScrollText,
  ServerCog,
  Square,
} from '@lucide/vue'
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { getDeploymentRun, listDeploymentRuns, stopDeployment } from '@/api/deployments'
import DeploymentRunStatusBadge from '@/components/business/DeploymentRunStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import type { DeploymentRunDetail, DeploymentRunSummary, DeploymentSummary } from '@/types/api'
import { useToast } from '@/composables/useToast'
import { formatDateTime, formatDuration } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    deployment: DeploymentSummary | null
    initialRunId?: number | null
  }>(),
  { initialRunId: null },
)
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const runs = ref<DeploymentRunSummary[]>([])
const selectedId = ref<number | null>(null)
const detail = ref<DeploymentRunDetail | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const refreshing = ref(false)
const error = ref<string | null>(null)
const logEl = ref<HTMLElement | null>(null)
const stickToBottom = ref(true)
const stopOpen = ref(false)
const stopping = ref(false)
let timer: number | undefined

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const hasActiveRun = computed(
  () =>
    runs.value.some((run) => run.status === 'QUEUED' || run.status === 'RUNNING') ||
    detail.value?.status === 'QUEUED' ||
    detail.value?.status === 'RUNNING',
)

const stopTimer = () => {
  window.clearInterval(timer)
  timer = undefined
}

const syncTimer = () => {
  stopTimer()
  if (open.value && hasActiveRun.value) {
    timer = window.setInterval(() => void refresh(), 2_000)
  }
}

const selectRun = async (runId: number) => {
  selectedId.value = runId
  detailLoading.value = true
  try {
    detail.value = await getDeploymentRun(runId)
  } catch (e) {
    error.value = messageOf(e)
    detail.value = null
  } finally {
    detailLoading.value = false
    syncTimer()
  }
}

const refresh = async () => {
  if (!props.deployment || refreshing.value) return
  refreshing.value = true
  try {
    const next = await listDeploymentRuns(props.deployment.id)
    runs.value = next
    if (selectedId.value !== null && next.some((run) => run.id === selectedId.value)) {
      detail.value = await getDeploymentRun(selectedId.value)
    } else if (selectedId.value !== null) {
      selectedId.value = null
      detail.value = null
    }
  } catch {
    // 轮询是补充信息：短暂断线时保留已有日志，不把弹框顶成错误页
  } finally {
    refreshing.value = false
    syncTimer()
  }
}

const initialize = async () => {
  stopTimer()
  runs.value = []
  detail.value = null
  selectedId.value = null
  error.value = null
  stickToBottom.value = true
  if (!props.deployment) return
  loading.value = true
  try {
    runs.value = await listDeploymentRuns(props.deployment.id)
    const target = props.initialRunId !== null
      && runs.value.some((run) => run.id === props.initialRunId)
      ? props.initialRunId
      : runs.value[0]?.id ?? null
    if (target !== null) await selectRun(target)
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loading.value = false
    syncTimer()
  }
}

const onLogScroll = () => {
  if (!logEl.value) return
  stickToBottom.value =
    logEl.value.scrollHeight - logEl.value.scrollTop - logEl.value.clientHeight < 24
}

const scrollLogToBottom = () => {
  if (!logEl.value) return
  stickToBottom.value = true
  logEl.value.scrollTop = logEl.value.scrollHeight
}

const requestStop = () => {
  if (!hasActiveRun.value) return
  stopOpen.value = true
}

const confirmStop = async () => {
  if (!props.deployment || stopping.value) return
  stopping.value = true
  try {
    const run = await stopDeployment(props.deployment.id)
    if (detail.value?.id === run.id) {
      detail.value = {
        ...detail.value,
        status: run.status,
        exitCode: run.exitCode,
        summary: run.summary,
        finishedAt: run.finishedAt,
      }
    }
    stopOpen.value = false
    toast.success('停止请求已提交', run.summary || '部署进程正在终止')
    await refresh()
  } catch (e) {
    toast.failure('停止部署失败', messageOf(e))
  } finally {
    stopping.value = false
  }
}

watch(
  [open, () => props.deployment?.id, () => props.initialRunId],
  ([opened]) => {
    if (opened) void initialize()
    else stopTimer()
  },
)

watch(
  () => detail.value?.log,
  async () => {
    if (!stickToBottom.value) return
    await nextTick()
    if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight
  },
)

onUnmounted(stopTimer)
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="log"
    height="fixed"
    title="部署执行记录"
    description="记录每次 SSH 部署的退出码、摘要与完整日志；密码不会出现在日志中。"
    :icon="ScrollText"
  >
    <div class="flex h-full min-h-0 flex-col gap-3">
      <p v-if="loading" class="text-caption text-label-tertiary">正在读取执行记录…</p>
      <p v-else-if="error" class="text-caption text-danger">{{ error }}</p>
      <div
        v-else-if="runs.length === 0"
        class="rounded-panel border border-separator-soft bg-content px-4 py-8 text-center"
      >
        <Icon :as="ServerCog" size="xlarge" class="mx-auto text-label-tertiary" />
        <p class="mt-2 text-body text-label-secondary">还没有执行记录</p>
        <p class="mt-1 text-caption text-label-tertiary">点击部署行右侧的播放按钮开始第一次部署。</p>
      </div>

      <template v-else>
        <div class="scroll-area h-[20vh] max-h-[170px] min-h-[112px] shrink-0 overflow-y-auto rounded-panel border border-separator-soft bg-content">
          <button
            v-for="run in runs"
            :key="run.id"
            type="button"
            class="flex w-full items-center gap-2 border-b border-separator-soft px-3 py-2 text-left transition-colors duration-100 ease-apple last:border-b-0 hover:bg-hover"
            :class="selectedId === run.id ? 'bg-selection' : ''"
            @click="selectRun(run.id)"
          >
            <span class="tnum shrink-0 text-caption text-label-tertiary">#{{ run.id }}</span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-caption font-emphasis text-label">
                {{ run.deploymentName }}
              </span>
              <span v-if="run.projectVersion" class="block truncate font-mono text-caption text-label-tertiary">
                {{ run.projectVersion }}
              </span>
            </span>
            <DeploymentRunStatusBadge :status="run.status" />
            <span class="tnum hidden shrink-0 text-caption text-label-tertiary sm:block">
              {{ formatDuration(run.startedAt, run.finishedAt) }}
            </span>
            <span class="tnum hidden w-28 shrink-0 text-right text-caption text-label-tertiary md:block">
              {{ formatDateTime(run.finishedAt ?? run.queuedAt) }}
            </span>
          </button>
        </div>

        <div class="flex min-h-0 flex-1 flex-col rounded-panel border border-separator-soft bg-content p-3">
          <p v-if="detailLoading" class="text-caption text-label-tertiary">正在读取日志…</p>
          <template v-else-if="detail">
            <div class="flex flex-wrap items-center gap-2">
              <Icon :as="FileText" size="inline" class="text-label-tertiary" />
              <p class="text-caption font-emphasis text-label">执行日志</p>
              <span class="text-caption text-label-tertiary">{{ detail.username }}@{{ detail.host }}:{{ detail.port }} · {{ detail.remoteDirectory }}</span>
              <span class="rounded-control bg-window px-2 py-0.5 font-mono text-caption text-label-secondary">
                {{ detail.projectVersion ?? '未标记版本' }}
              </span>
              <DeploymentRunStatusBadge :status="detail.status" />
              <span v-if="detail.exitCode !== null" class="tnum text-caption text-label-tertiary">
                exit {{ detail.exitCode }}
              </span>
              <div class="ml-auto flex items-center gap-2">
                <Tooltip v-if="!stickToBottom" content="跳到最新日志">
                  <IconButton label="跳到最新日志" size="sm" @click="scrollLogToBottom">
                    <Icon :as="ArrowDown" size="regular" />
                  </IconButton>
                </Tooltip>
                <span v-if="hasActiveRun" class="text-caption text-label-tertiary">每 2 秒刷新</span>
              </div>
            </div>
            <div class="mt-1 flex min-h-0 flex-1 flex-col">
              <p
                v-if="detail.summary"
                class="mb-2 line-clamp-2 shrink-0 text-caption text-label-secondary"
                :title="detail.summary"
              >
                {{ detail.summary }}
              </p>
              <pre
                ref="logEl"
                class="deployment-log min-h-[120px] flex-1 touch-pan-y overflow-y-scroll overscroll-contain whitespace-pre-wrap break-words rounded-panel bg-window py-2 pl-3 pr-2 font-mono text-caption leading-relaxed text-label"
                role="region"
                aria-label="部署执行日志"
                tabindex="0"
                @scroll="onLogScroll"
                @wheel.stop
              >{{ detail.log || '（部署还在等待输出）' }}</pre>
            </div>
          </template>
          <p v-else class="text-caption text-label-tertiary">选择一条记录查看日志。</p>
        </div>
      </template>
    </div>

    <template #footer>
      <Button v-if="hasActiveRun" variant="danger" @click="requestStop">
        <Icon :as="Square" size="inline" />
        {{ stopping ? '停止中…' : '停止部署' }}
      </Button>
      <Button @click="open = false">关闭</Button>
    </template>
  </Dialog>

  <Dialog
    v-model:open="stopOpen"
    title="停止当前部署？"
    description="平台会终止正在执行的 SSH 或本机发布进程。"
    :icon="AlertTriangle"
    icon-tone="danger"
  >
    <p class="text-caption text-label-secondary">
      已经完成的服务器变更不会自动回滚。停止后请查看最终日志，确认部署停在哪个步骤。
    </p>
    <template #footer>
      <Button :disabled="stopping" @click="stopOpen = false">继续执行</Button>
      <Button variant="danger" :disabled="stopping" @click="confirmStop">
        {{ stopping ? '停止中…' : '确认停止' }}
      </Button>
    </template>
  </Dialog>
</template>

<style scoped>
/*
 * 部署日志是独立滚动区：滚动条常驻，避免用户把鼠标移出日志后失去可视反馈；
 * 固定槽位也防止出现滚动条时正文横向跳动。
 */
.deployment-log {
  scrollbar-gutter: stable;
  scrollbar-color: var(--fx-scrollbar-thumb) transparent;
  scrollbar-width: thin;
}

.deployment-log::-webkit-scrollbar {
  width: 12px;
  height: 12px;
}

.deployment-log::-webkit-scrollbar-track {
  background: transparent;
}

.deployment-log::-webkit-scrollbar-thumb {
  min-height: 36px;
  border: 3px solid transparent;
  border-radius: 999px;
  background-color: var(--fx-scrollbar-thumb);
  background-clip: content-box;
}

.deployment-log::-webkit-scrollbar-thumb:hover {
  background-color: var(--label-secondary);
}
</style>
