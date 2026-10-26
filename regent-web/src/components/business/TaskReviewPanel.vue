<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { AlertTriangle, Check, FileCode2, GitBranch, LoaderCircle, RefreshCw, ShieldCheck } from '@lucide/vue'

import {
  decideTaskReview,
  decideReviewUnit,
  generateTaskReview,
  getTaskReview,
  getTaskReviewDiff,
  markReviewUnitViewed,
  regenerateTaskReview,
  updateReviewFinding,
} from '@/api/reviews'
import { getGitCommitSettings } from '@/api/settings'
import { ApiError } from '@/api/http'
import { getTaskGitCommits } from '@/api/gitCommits'
import Button from '@/components/ui/button/Button.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import TaskReviewWorkspace from '@/components/business/TaskReviewWorkspace.vue'
import GitCommitDialog from '@/components/business/GitCommitDialog.vue'
import GitCommitStatusBadge from '@/components/business/GitCommitStatusBadge.vue'
import { useToast } from '@/composables/useToast'
import { formatDateTime } from '@/utils/format'
import type {
  ReviewDecisionType,
  ReviewFindingStatus,
  ReviewGenerationProgress,
  ReviewRiskLevel,
  ReviewSnapshotStatus,
  ReviewUnitDecisionStatus,
  TaskReview,
  GitCommitSummary,
} from '@/types/api'
import type { TaskStatus } from '@/types/task'

const props = defineProps<{
  taskId: number
  taskStatus: TaskStatus
}>()
const emit = defineEmits<{ openRun: [runId: number] }>()
const toast = useToast()
const router = useRouter()

const review = ref<TaskReview | null>(null)
const loading = ref(false)
const acting = ref(false)
const error = ref<string | null>(null)
const workspaceOpen = ref(false)
const gitCommitOpen = ref(false)
const gitCommitEnabled = ref(true)
const gitCommits = ref<GitCommitSummary[]>([])
const fullDiff = ref<string | null>(null)
const fullDiffTruncated = ref(false)
const loadingDiff = ref(false)
let retryTimer: number | undefined
let retryCount = 0
let evidenceTimer: number | undefined
let evidenceRetryCount = 0

const snapshot = computed(() => review.value?.snapshot ?? null)
const progress = computed<ReviewGenerationProgress>(() =>
  review.value?.progress ?? {
    status: 'IDLE',
    stage: 'idle',
    message: '尚未开始生成审阅',
    updatedAt: null,
    error: null,
  },
)
const isGenerating = computed(
  () => progress.value.status === 'QUEUED' || progress.value.status === 'RUNNING',
)
const reviewOpen = computed(() =>
  snapshot.value?.status === 'WAITING_REVIEW' || snapshot.value?.status === 'IN_REVIEW',
)
const canGenerate = computed(
  () =>
    !loading.value
    && !acting.value
    && props.taskStatus === 'DONE'
    && snapshot.value === null
    && !isGenerating.value,
)
const riskCounts = computed(() => {
  const counts: Record<ReviewRiskLevel, number> = { HIGH: 0, MEDIUM: 0, LOW: 0 }
  for (const unit of snapshot.value?.changeUnits ?? []) counts[unit.riskLevel] += 1
  return counts
})
const approvedRequiredUnitCount = computed(
  () =>
    snapshot.value?.changeUnits.filter(
      (unit) => unit.requiresView && unit.decisionStatus === 'APPROVED',
    ).length ?? 0,
)
const requiredProgressPercent = computed(() => {
  const total = snapshot.value?.requiredUnitCount ?? 0
  if (total === 0) return 100
  return Math.min(100, (approvedRequiredUnitCount.value / total) * 100)
})
const progressSteps = [
  { stage: 'queued', label: '准备' },
  { stage: 'diff', label: '读取变更' },
  { stage: 'analysis', label: 'AI 预审' },
  { stage: 'saving', label: '保存结果' },
] as const
const progressRank = computed(() => {
  const stage = progress.value.stage === 'fallback' ? 'analysis' : progress.value.stage
  const index = progressSteps.findIndex((step) => step.stage === stage)
  return index < 0 ? (progress.value.status === 'COMPLETED' ? progressSteps.length : -1) : index
})

const statusLabel: Record<ReviewSnapshotStatus, string> = {
  WAITING_REVIEW: '待审阅',
  IN_REVIEW: '审阅中',
  CHANGES_REQUESTED: '待修改',
  APPROVED: '已通过',
  COMMITTED: '已提交',
  REJECTED: '已驳回',
  EXPIRED: '已过期',
}
const reviewStatusLabel = computed(() => {
  if (!snapshot.value) return ''
  return snapshot.value.canApprove ? '待整单通过' : statusLabel[snapshot.value.status]
})
const reviewStatusDot = computed(() => {
  if (!snapshot.value) return 'bg-neutral'
  if (snapshot.value.status === 'APPROVED' || snapshot.value.status === 'COMMITTED') {
    return 'bg-success'
  }
  if (snapshot.value.status === 'EXPIRED' || snapshot.value.status === 'REJECTED') {
    return 'bg-danger'
  }
  if (snapshot.value.status === 'CHANGES_REQUESTED' || snapshot.value.canApprove) {
    return 'bg-warning'
  }
  return 'bg-accent'
})

async function load() {
  loading.value = true
  error.value = null
  try {
    review.value = await getTaskReview(props.taskId)
    await loadGitCommits()
    if (snapshot.value) await loadGitCommitAvailability()
    retryCount = 0
  } catch (cause) {
    error.value = messageOf(cause)
  } finally {
    loading.value = false
  }
  scheduleAutoRefresh()
  scheduleEvidenceRefresh()
}

async function loadGitCommits() {
  try {
    gitCommits.value = await getTaskGitCommits(props.taskId)
  } catch {
    gitCommits.value = []
  }
}

const latestGitCommit = computed(() => gitCommits.value[0] ?? null)
const gitActionLabel = computed(() => {
  if (!snapshot.value) return '提交 Git'
  if (snapshot.value.status === 'APPROVED') return '提交 Git'
  if (snapshot.value.status === 'EXPIRED') return '重新审阅'
  if (snapshot.value.canApprove) return '通过整单'
  return '查看审阅'
})
const gitActionIcon = computed(() => {
  if (snapshot.value?.status === 'APPROVED') return GitBranch
  if (snapshot.value?.status === 'EXPIRED') return RefreshCw
  if (snapshot.value?.canApprove) return Check
  return FileCode2
})

function runGitAction() {
  if (snapshot.value?.status === 'APPROVED') {
    gitCommitOpen.value = true
    return
  }
  workspaceOpen.value = true
}

function openGitCommits() {
  void router.push({
    path: '/delivery',
    query: { view: 'git-commits', taskId: String(props.taskId) },
  })
}

async function loadGitCommitAvailability() {
  try {
    gitCommitEnabled.value = (await getGitCommitSettings()).enabled
  } catch {
    gitCommitEnabled.value = true
  }
}

function scheduleAutoRefresh() {
  window.clearTimeout(retryTimer)
  if (
    (snapshot.value && !isGenerating.value)
    || props.taskStatus !== 'DONE'
    || progress.value.status === 'FAILED'
    || retryCount >= 90
  ) return
  retryCount += 1
  retryTimer = window.setTimeout(() => void load(), 1500)
}

function scheduleEvidenceRefresh() {
  window.clearTimeout(evidenceTimer)
  const waiting = snapshot.value?.evidence.some((item) => item.status === 'NOT_EXECUTED') ?? false
  if (!snapshot.value || !reviewOpen.value || !waiting || evidenceRetryCount >= 20) return
  evidenceRetryCount += 1
  evidenceTimer = window.setTimeout(() => void load(), 3000)
}

async function generate() {
  if (!canGenerate.value) return
  acting.value = true
  error.value = null
  try {
    review.value = await generateTaskReview(props.taskId)
    retryCount = 0
    scheduleAutoRefresh()
    toast.show('已开始生成变更审阅', { description: '进度会自动刷新' })
  } catch (cause) {
    error.value = messageOf(cause)
  } finally {
    acting.value = false
  }
}

async function regenerate() {
  if (acting.value || isGenerating.value) return
  acting.value = true
  error.value = null
  try {
    review.value = await regenerateTaskReview(props.taskId)
    retryCount = 0
    scheduleAutoRefresh()
    toast.show('已开始重新预审', { description: '旧快照会保留，新结果完成后自动切换' })
  } catch (cause) {
    toast.warning('重新预审失败', messageOf(cause))
  } finally {
    acting.value = false
  }
}

async function handleMarkViewed(unitId: number) {
  try {
    const updated = await markReviewUnitViewed(props.taskId, unitId)
    if (review.value) review.value.snapshot = updated
  } catch (cause) {
    toast.warning('查看状态保存失败', messageOf(cause))
  }
}

async function handleFindingStatus(findingId: number, status: ReviewFindingStatus) {
  if (acting.value) return
  acting.value = true
  try {
    const updated = await updateReviewFinding(props.taskId, findingId, status)
    if (review.value) review.value.snapshot = updated
  } catch (cause) {
    toast.warning('发现状态更新失败', messageOf(cause))
  } finally {
    acting.value = false
  }
}

async function handleUnitDecision(
  unitId: number,
  decision: ReviewUnitDecisionStatus,
  comment: string | null,
) {
  if (acting.value) return
  acting.value = true
  try {
    const updated = await decideReviewUnit(props.taskId, unitId, decision, comment)
    if (review.value) review.value.snapshot = updated
  } catch (cause) {
    toast.warning('diff 决策提交失败', messageOf(cause))
  } finally {
    acting.value = false
  }
}

async function handleDecision(decision: ReviewDecisionType, comment: string | null) {
  if (acting.value) return
  if (decision !== 'APPROVED' && !comment?.trim()) {
    toast.warning('请填写审阅意见')
    return
  }
  acting.value = true
  try {
    const result = await decideTaskReview(props.taskId, decision, comment?.trim() || null)
    if (review.value) review.value.snapshot = result.snapshot
    review.value = await getTaskReview(props.taskId)
    if (decision === 'APPROVED') {
      await loadGitCommitAvailability()
      toast.success('当前变更已通过审阅', '可继续生成并提交 Git 说明')
      if (gitCommitEnabled.value) gitCommitOpen.value = true
    }
    else if (decision === 'CHANGES_REQUESTED') {
      toast.show('已创建原会话修改任务', {
        description: result.continuationTaskId ? `后续任务 #${result.continuationTaskId}` : undefined,
      })
    } else {
      toast.show('本次变更已驳回')
    }
  } catch (cause) {
    toast.warning('审阅结论提交失败', messageOf(cause))
  } finally {
    acting.value = false
  }
}

async function loadDiff() {
  if (fullDiff.value !== null || loadingDiff.value) return
  loadingDiff.value = true
  try {
    const diff = await getTaskReviewDiff(props.taskId)
    fullDiff.value = diff.content
    fullDiffTruncated.value = diff.truncated
  } catch (cause) {
    toast.warning('完整 diff 读取失败', messageOf(cause))
  } finally {
    loadingDiff.value = false
  }
}

function toggleDiff() {
  if (fullDiff.value !== null) {
    fullDiff.value = null
  } else {
    void loadDiff()
  }
}

function goDeploy() {
  if (!snapshot.value) return
  void router.push({
    path: '/deployments',
    query: {
      projectId: String(snapshot.value.projectId),
      taskId: String(snapshot.value.taskId),
    },
  })
}

function messageOf(cause: unknown): string {
  return cause instanceof ApiError ? cause.message : '请求失败，请稍后重试'
}

watch(() => props.taskId, () => {
  review.value = null
  fullDiff.value = null
  workspaceOpen.value = false
  retryCount = 0
  evidenceRetryCount = 0
  window.clearTimeout(evidenceTimer)
  void load()
}, { immediate: true })

onUnmounted(() => {
  window.clearTimeout(retryTimer)
  window.clearTimeout(evidenceTimer)
})
</script>

<template>
  <section class="mt-4 border-t border-separator pt-3">
    <div class="flex min-w-0 items-center gap-2">
      <ShieldCheck :size="15" class="shrink-0 text-label-secondary" />
      <p class="shrink-0 text-caption font-emphasis text-label">变更审阅</p>
      <template v-if="snapshot">
        <span class="h-3.5 w-px shrink-0 bg-separator" aria-hidden="true" />
        <StatusDot :dot="reviewStatusDot" class="min-w-0 text-caption text-label-secondary">
          {{ reviewStatusLabel }}
        </StatusDot>
      </template>
      <Tooltip content="刷新审阅">
        <button
          type="button"
          class="ml-auto rounded-control p-1 text-label-tertiary transition-colors hover:bg-hover hover:text-label"
          aria-label="刷新审阅"
          :disabled="loading"
          @click="load"
        >
          <RefreshCw :size="13" :class="loading ? 'animate-spin' : ''" />
        </button>
      </Tooltip>
    </div>

    <p v-if="error" class="mt-2 text-caption text-danger">{{ error }}</p>
    <p v-else-if="loading && !review" class="mt-2 text-caption text-label-tertiary">正在读取审阅状态…</p>
    <div v-else-if="!snapshot" class="mt-2">
      <div v-if="isGenerating" class="rounded-panel border border-separator-soft bg-content px-2.5 py-2">
        <div class="flex items-center gap-2">
          <LoaderCircle :size="14" class="shrink-0 animate-spin text-accent" />
          <p class="text-caption font-emphasis text-label">{{ progress.message }}</p>
        </div>
        <div class="mt-2 flex items-center gap-1">
          <template v-for="(step, index) in progressSteps" :key="step.stage">
            <div class="size-1.5 rounded-full" :class="index <= progressRank ? 'bg-accent' : 'bg-separator'" />
            <span class="text-caption" :class="index <= progressRank ? 'text-label-secondary' : 'text-label-tertiary'">
              {{ step.label }}
            </span>
            <div
              v-if="index < progressSteps.length - 1"
              class="h-px min-w-2 flex-1"
              :class="index < progressRank ? 'bg-accent/50' : 'bg-separator'"
            />
          </template>
        </div>
        <p v-if="progress.updatedAt" class="tnum mt-1.5 text-caption text-label-tertiary">
          更新于 {{ formatDateTime(progress.updatedAt) }}
        </p>
      </div>
      <div
        v-else-if="progress.status === 'FAILED'"
        class="rounded-panel border border-danger/20 bg-danger/5 px-2.5 py-2"
      >
        <div class="flex items-start gap-1.5">
          <AlertTriangle :size="13" class="mt-0.5 shrink-0 text-danger" />
          <div class="min-w-0 flex-1">
            <p class="text-caption font-emphasis text-label">{{ progress.message }}</p>
            <p v-if="progress.error" class="mt-0.5 text-caption text-label-tertiary">{{ progress.error }}</p>
          </div>
        </div>
        <Button size="sm" class="mt-2" :disabled="acting || taskStatus !== 'DONE'" @click="generate">
          <Icon :as="RefreshCw" size="inline" />重新生成
        </Button>
      </div>
      <template v-else>
        <p class="text-caption text-label-tertiary">
          {{ taskStatus === 'DONE' ? '审阅快照尚未生成。' : '任务完成后自动生成审阅快照。' }}
        </p>
        <Button v-if="canGenerate" size="sm" class="mt-2" :disabled="acting" @click="generate">
          <Icon :as="FileCode2" size="inline" />生成审阅
        </Button>
      </template>
    </div>

    <template v-else>
      <div class="mt-2.5 overflow-hidden rounded-panel border border-separator-soft bg-content">
        <div class="px-3 py-2.5">
          <div class="flex items-baseline gap-1.5">
            <span class="tnum text-title font-emphasis text-label">{{ snapshot.fileCount }}</span>
            <span class="text-caption text-label-secondary">个文件</span>
            <span class="ml-auto tnum text-caption">
              <span class="text-success">+{{ snapshot.additions }}</span>
              <span class="px-1 text-label-tertiary">/</span>
              <span class="text-danger">-{{ snapshot.deletions }}</span>
            </span>
          </div>

          <div class="mt-2.5 flex h-1 overflow-hidden rounded-full bg-separator-soft" aria-hidden="true">
            <span
              v-if="riskCounts.HIGH > 0"
              class="bg-danger"
              :style="{ flexGrow: riskCounts.HIGH }"
            />
            <span
              v-if="riskCounts.MEDIUM > 0"
              class="bg-warning"
              :style="{ flexGrow: riskCounts.MEDIUM }"
            />
            <span
              v-if="riskCounts.LOW > 0"
              class="bg-neutral/70"
              :style="{ flexGrow: riskCounts.LOW }"
            />
          </div>

          <div class="mt-2 grid grid-cols-3 gap-2 text-caption text-label-secondary">
            <span class="inline-flex min-w-0 items-center gap-1">
              <span class="size-1.5 shrink-0 rounded-full bg-danger" />
              高风险 <strong class="tnum font-emphasis text-label">{{ riskCounts.HIGH }}</strong>
            </span>
            <span class="inline-flex min-w-0 items-center gap-1">
              <span class="size-1.5 shrink-0 rounded-full bg-warning" />
              中风险 <strong class="tnum font-emphasis text-label">{{ riskCounts.MEDIUM }}</strong>
            </span>
            <span class="inline-flex min-w-0 items-center gap-1">
              <span class="size-1.5 shrink-0 rounded-full bg-neutral/70" />
              低风险 <strong class="tnum font-emphasis text-label">{{ riskCounts.LOW }}</strong>
            </span>
          </div>
        </div>

        <div class="h-px bg-separator-soft" />

        <div class="px-3 py-2">
          <div class="flex items-center gap-2 text-caption">
            <span class="text-label-secondary">必看通过</span>
            <span class="ml-auto tnum font-emphasis text-label">
              {{ approvedRequiredUnitCount }} / {{ snapshot.requiredUnitCount }}
            </span>
          </div>
          <div class="mt-1.5 h-1 overflow-hidden rounded-full bg-separator-soft">
            <div
              class="h-full rounded-full bg-success transition-[width] duration-200 ease-apple"
              :style="{ width: `${requiredProgressPercent}%` }"
            />
          </div>
          <p class="mt-2 truncate font-mono text-caption text-label-tertiary">
            <template v-if="snapshot.gitRepository">
              {{ snapshot.baseRef?.slice(0, 8) }} → {{ snapshot.headRef?.slice(0, 8) }}
            </template>
            <template v-else>文件清单模式</template>
          </p>
        </div>
      </div>

      <div
        v-if="snapshot.changeRequestedUnitCount > 0 || snapshot.rejectedUnitCount > 0 || snapshot.unresolvedBlockerCount > 0"
        class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-caption"
      >
        <span v-if="snapshot.changeRequestedUnitCount > 0" class="text-caption text-warning">
          {{ snapshot.changeRequestedUnitCount }} 项要求修改
        </span>
        <span v-if="snapshot.rejectedUnitCount > 0" class="text-caption text-danger">
          {{ snapshot.rejectedUnitCount }} 项驳回
        </span>
        <span v-if="snapshot.unresolvedBlockerCount > 0" class="tnum text-caption text-danger">
          {{ snapshot.unresolvedBlockerCount }} 个阻断
        </span>
      </div>

      <div
        v-if="isGenerating"
        class="mt-2 flex items-center gap-2 rounded-panel border border-accent/20 bg-accent/5 px-2.5 py-2"
      >
        <LoaderCircle :size="13" class="animate-spin text-accent" />
        <span class="text-caption text-label">{{ progress.message }}</span>
      </div>

      <div class="mt-2 flex flex-wrap gap-2">
        <Button variant="primary" size="sm" class="flex-1" @click="workspaceOpen = true">
          <Icon :as="FileCode2" size="inline" />打开审阅工作区
        </Button>
        <Button
          v-if="snapshot.status !== 'COMMITTED' && gitCommitEnabled"
          size="sm"
          :variant="snapshot.status === 'APPROVED' || snapshot.canApprove ? 'primary' : 'default'"
          @click="runGitAction"
        >
          <Icon :as="gitActionIcon" size="inline" />{{ gitActionLabel }}
        </Button>
        <Button v-if="snapshot.deployable" size="sm" @click="goDeploy">去部署</Button>
      </div>

      <p v-if="snapshot.reviewedAt" class="mt-2 text-caption text-label-tertiary">
        最近结论 {{ formatDateTime(snapshot.reviewedAt) }}
      </p>
      <button
        v-if="latestGitCommit"
        type="button"
        class="mt-2 w-full rounded-panel border border-separator-soft bg-content px-2.5 py-2 text-left transition-colors hover:bg-hover"
        @click="openGitCommits"
      >
        <div class="flex items-center gap-2">
          <GitCommitStatusBadge :status="latestGitCommit.status" />
          <span class="min-w-0 flex-1 truncate text-caption font-emphasis text-label">
            {{ latestGitCommit.message }}
          </span>
        </div>
        <p class="mt-1 break-all font-mono text-caption text-label-tertiary">
          {{ latestGitCommit.branchName }} · {{ latestGitCommit.commitSha?.slice(0, 12) ?? '未生成 Commit' }}
        </p>
        <p v-if="latestGitCommit.failureReason" class="mt-1 text-caption text-danger">
          {{ latestGitCommit.failureReason }}
        </p>
        <p class="mt-1 text-caption text-accent">
          查看全部 {{ gitCommits.length }} 次提交
        </p>
      </button>
    </template>
  </section>

  <TaskReviewWorkspace
    v-if="snapshot"
    v-model:open="workspaceOpen"
    :snapshot="snapshot"
    :decisions="review?.decisions ?? []"
    :acting="acting"
    :full-diff="fullDiff"
    :full-diff-truncated="fullDiffTruncated"
    :loading-diff="loadingDiff"
    :progress="progress"
    :git-commit-enabled="gitCommitEnabled"
    @mark-unit-viewed="handleMarkViewed"
    @finding-status="handleFindingStatus"
    @unit-decision="handleUnitDecision"
    @decision="handleDecision"
    @toggle-diff="toggleDiff"
    @open-run="emit('openRun', $event)"
    @regenerate="regenerate"
    @commit-git="gitCommitOpen = true"
  />

  <GitCommitDialog
    v-if="snapshot"
    v-model:open="gitCommitOpen"
    :task-id="taskId"
    :revision="snapshot.id + ':' + snapshot.updatedAt"
    @committed="load"
  />
</template>
