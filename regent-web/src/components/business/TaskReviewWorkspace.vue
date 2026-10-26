<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  ArrowLeft,
  Check,
  Code2,
  ExternalLink,
  FileCode2,
  FileDiff,
  GitBranch,
  LoaderCircle,
  MessageSquare,
  RefreshCw,
  ShieldCheck,
  X,
} from '@lucide/vue'

import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import SectionTabs from '@/components/ui/section-tabs/SectionTabs.vue'
import DiffViewer from '@/components/business/DiffViewer.vue'
import CodeEditorDialog from '@/components/business/CodeEditorDialog.vue'
import { formatDateTime } from '@/utils/format'
import type {
  ReviewChangeUnit,
  ReviewDecision,
  ReviewDecisionType,
  ReviewEvidence,
  ReviewFinding,
  ReviewFindingStatus,
  ReviewGenerationProgress,
  ReviewRiskLevel,
  ReviewSnapshot,
  ReviewUnitDecisionStatus,
} from '@/types/api'

const props = defineProps<{
  snapshot: ReviewSnapshot
  decisions: ReviewDecision[]
  acting: boolean
  fullDiff: string | null
  fullDiffTruncated: boolean
  loadingDiff: boolean
  progress: ReviewGenerationProgress
  gitCommitEnabled: boolean
}>()
const emit = defineEmits<{
  'mark-unit-viewed': [unitId: number]
  'finding-status': [findingId: number, status: ReviewFindingStatus]
  'unit-decision': [unitId: number, decision: ReviewUnitDecisionStatus, comment: string | null]
  decision: [decision: ReviewDecisionType, comment: string | null]
  'toggle-diff': []
  'open-run': [runId: number]
  regenerate: []
  'commit-git': []
}>()
const open = defineModel<boolean>('open', { default: false })

type WorkspaceTab = 'units' | 'findings' | 'evidence' | 'diff'
const activeTab = ref<WorkspaceTab>('units')
const selectedUnitId = ref<number | null>(null)
const highRiskOnly = ref(false)
const showSelectedDiff = ref(false)
const pendingDecision = ref<Exclude<ReviewDecisionType, 'APPROVED'> | null>(null)
const comment = ref('')
const pendingUnitDecision = ref<Exclude<ReviewUnitDecisionStatus, 'APPROVED'> | null>(null)
const unitComment = ref('')
const codeEditorOpen = ref(false)

const openDecision = computed(() =>
  props.snapshot.status === 'WAITING_REVIEW' || props.snapshot.status === 'IN_REVIEW',
)
const isRegenerating = computed(
  () => props.progress.status === 'QUEUED' || props.progress.status === 'RUNNING',
)
const riskCounts = computed(() => {
  const counts: Record<ReviewRiskLevel, number> = { HIGH: 0, MEDIUM: 0, LOW: 0 }
  for (const unit of props.snapshot.changeUnits) counts[unit.riskLevel] += 1
  return counts
})
const units = computed(() => {
  const riskOrder: Record<ReviewRiskLevel, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 }
  return [...props.snapshot.changeUnits]
    .filter((unit) => !highRiskOnly.value || unit.riskLevel === 'HIGH')
    .sort((left, right) =>
      riskOrder[left.riskLevel] - riskOrder[right.riskLevel]
      || left.sequenceNo - right.sequenceNo,
    )
})
const selectedUnit = computed(
  () => units.value.find((unit) => unit.id === selectedUnitId.value) ?? units.value[0] ?? null,
)
const findings = computed(() =>
  [...props.snapshot.findings].sort(
    (left, right) => severityRank(left.severity) - severityRank(right.severity),
  ),
)
const requiredUnits = computed(() =>
  props.snapshot.changeUnits.filter((unit) => unit.requiresView),
)
const optionalUnits = computed(() =>
  props.snapshot.changeUnits.filter((unit) => !unit.requiresView),
)
const pendingRequiredUnits = computed(() =>
  requiredUnits.value.filter((unit) => unit.decisionStatus !== 'APPROVED'),
)
const approvedRequiredUnitCount = computed(() =>
  requiredUnits.value.filter((unit) => unit.decisionStatus === 'APPROVED').length,
)
const evidence = computed(() =>
  [...props.snapshot.evidence].sort(
    (left, right) => Date.parse(right.occurredAt) - Date.parse(left.occurredAt),
  ),
)
const tabs = computed(() => [
  { value: 'units', label: '变更单元', badge: props.snapshot.changeUnits.length },
  { value: 'findings', label: '审阅发现', badge: props.snapshot.findings.length },
  { value: 'evidence', label: '验证证据', badge: props.snapshot.evidence.length },
  { value: 'diff', label: '完整 diff' },
])
const decisionCount = computed(() => props.decisions.length)
const reviewGuidance = computed(() => {
  if (props.snapshot.unresolvedBlockerCount > 0) {
    return `先处理 ${props.snapshot.unresolvedBlockerCount} 个阻断问题，再确认 ${requiredUnits.value.length} 个重点单元。`
  }
  if (requiredUnits.value.length === 0) {
    return `当前没有必须逐项决策的变更；已通过 ${props.snapshot.approvedUnitCount} 项，其余可作为交付背景快速浏览。`
  }
  if (props.snapshot.rejectedUnitCount > 0) {
    return `有 ${props.snapshot.rejectedUnitCount} 个 diff 被驳回，整单不能通过。`
  }
  if (props.snapshot.changeRequestedUnitCount > 0) {
    return `有 ${props.snapshot.changeRequestedUnitCount} 个 diff 要求修改，整单不能通过。`
  }
  if (pendingRequiredUnits.value.length === 0) {
    return `必看 diff 已全部通过，其余 ${optionalUnits.value.length} 个单元可按需决策。`
  }
  return `还需逐项决策 ${pendingRequiredUnits.value.length} 个必看 diff；重点看每项列出的确认问题。`
})

function severityRank(severity: ReviewFinding['severity']): number {
  return { BLOCKER: 0, CRITICAL: 1, MAJOR: 2, MINOR: 3, INFO: 4 }[severity]
}

function riskTone(risk: ReviewRiskLevel): 'danger' | 'warning' | 'neutral' {
  return risk === 'HIGH' ? 'danger' : risk === 'MEDIUM' ? 'warning' : 'neutral'
}

function findingTone(finding: ReviewFinding): 'danger' | 'warning' | 'neutral' {
  return finding.severity === 'BLOCKER' || finding.severity === 'CRITICAL'
    ? 'danger'
    : finding.severity === 'MAJOR'
      ? 'warning'
      : 'neutral'
}

function evidenceTone(status: ReviewEvidence['status']): 'success' | 'danger' | 'warning' | 'neutral' {
  if (status === 'PASSED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'NOT_EXECUTED') return 'warning'
  return 'neutral'
}

function findingStatusLabel(status: ReviewFindingStatus): string {
  return {
    PENDING: '待确认',
    CONFIRMED: '属实',
    FALSE_POSITIVE: '误报',
    FIXED: '已修复',
  }[status]
}

function unitDecisionLabel(status: ReviewUnitDecisionStatus): string {
  return {
    PENDING: '待决策',
    APPROVED: '已通过',
    CHANGES_REQUESTED: '要求修改',
    REJECTED: '已驳回',
  }[status]
}

function unitDecisionTone(status: ReviewUnitDecisionStatus): 'neutral' | 'success' | 'warning' | 'danger' {
  return status === 'APPROVED'
    ? 'success'
    : status === 'CHANGES_REQUESTED' || status === 'PENDING'
      ? 'warning'
      : status === 'REJECTED'
        ? 'danger'
        : 'neutral'
}

function unitDecisionDot(status: ReviewUnitDecisionStatus): string {
  return status === 'APPROVED'
    ? 'bg-success'
    : status === 'REJECTED'
      ? 'bg-danger'
      : status === 'CHANGES_REQUESTED'
        ? 'bg-warning'
        : 'bg-accent'
}

function evidenceStatusLabel(status: ReviewEvidence['status']): string {
  return {
    PASSED: '通过',
    FAILED: '失败',
    NOT_EXECUTED: '未执行',
    UNAVAILABLE: '不可验证',
  }[status]
}

function selectUnit(unit: ReviewChangeUnit) {
  selectedUnitId.value = unit.id
  showSelectedDiff.value = false
  pendingUnitDecision.value = null
  unitComment.value = ''
  if (openDecision.value && !unit.viewedAt) emit('mark-unit-viewed', unit.id)
}

function reviewUnit(unit: ReviewChangeUnit) {
  activeTab.value = 'units'
  selectUnit(unit)
}

function selectTab(value: string) {
  activeTab.value = value as WorkspaceTab
  if (activeTab.value === 'diff' && props.fullDiff === null) emit('toggle-diff')
}

function beginDecision(decision: Exclude<ReviewDecisionType, 'APPROVED'>) {
  pendingDecision.value = decision
  comment.value = ''
}

function beginUnitDecision(decision: Exclude<ReviewUnitDecisionStatus, 'APPROVED'>) {
  if (!selectedUnit.value || selectedUnit.value.decisionStatus !== 'PENDING') return
  pendingUnitDecision.value = decision
  unitComment.value = ''
}

function submitUnitDecision(decision: ReviewUnitDecisionStatus) {
  if (!selectedUnit.value || props.acting) return
  const text = unitComment.value.trim()
  if (decision !== 'APPROVED' && !text) return
  emit('unit-decision', selectedUnit.value.id, decision, text || null)
  pendingUnitDecision.value = null
  unitComment.value = ''
}

function submitDecision(decision: ReviewDecisionType, commentText: string | null = null) {
  if (props.acting) return
  if (decision !== 'APPROVED' && !commentText?.trim()) return
  emit('decision', decision, commentText?.trim() || null)
  pendingDecision.value = null
  comment.value = ''
}

function openRun(item: ReviewEvidence) {
  if (item.sourceType === 'TASK_RUN' && item.sourceId !== null) {
    emit('open-run', item.sourceId)
  }
}

watch(
  () => props.snapshot.id,
  () => {
    highRiskOnly.value = false
    activeTab.value = props.snapshot.unresolvedBlockerCount > 0 ? 'findings' : 'units'
    selectedUnitId.value =
      props.snapshot.changeUnits.find((unit) => unit.requiresView)?.id
      ?? props.snapshot.changeUnits.find((unit) => unit.riskLevel === 'HIGH')?.id
      ?? props.snapshot.changeUnits[0]?.id
      ?? null
    pendingDecision.value = null
    comment.value = ''
    pendingUnitDecision.value = null
    unitComment.value = ''
  },
  { immediate: true },
)

watch(units, (value) => {
  if (!value.some((unit) => unit.id === selectedUnitId.value)) {
    selectedUnitId.value = value[0]?.id ?? null
  }
})
</script>

<template>
  <Dialog
    v-model:open="open"
    fullscreen
    layout="log"
    size="wide"
    title="变更审阅"
    :description="`任务 #${snapshot.taskId} · 运行 #${snapshot.taskRunId} · ${snapshot.fileCount} 个文件`"
  >
    <template #header-leading>
      <IconButton label="返回任务" size="regular" @click="open = false">
        <Icon :as="ArrowLeft" size="regular" />
      </IconButton>
    </template>

    <div class="flex h-full min-h-0 flex-col">
      <section class="grid shrink-0 grid-cols-2 gap-2 border-b border-separator pb-3 lg:grid-cols-6">
        <div class="rounded-panel bg-danger/6 px-3 py-2">
          <p class="text-caption text-label-tertiary">高风险单元</p>
          <p class="tnum mt-0.5 text-title font-emphasis text-danger">{{ riskCounts.HIGH }}</p>
        </div>
        <div class="rounded-panel bg-warning/6 px-3 py-2">
          <p class="text-caption text-label-tertiary">中风险单元</p>
          <p class="tnum mt-0.5 text-title font-emphasis text-warning">{{ riskCounts.MEDIUM }}</p>
        </div>
        <div class="rounded-panel bg-content px-3 py-2">
          <p class="text-caption text-label-tertiary">低风险单元</p>
          <p class="tnum mt-0.5 text-title font-emphasis text-label">{{ riskCounts.LOW }}</p>
        </div>
        <div class="rounded-panel bg-content px-3 py-2">
          <p class="text-caption text-label-tertiary">代码变更</p>
          <p class="mt-0.5 text-caption font-emphasis text-label">
            {{ snapshot.fileCount }} 文件
            <span class="text-success">+{{ snapshot.additions }}</span>
            <span class="text-danger">-{{ snapshot.deletions }}</span>
          </p>
        </div>
        <div class="rounded-panel bg-content px-3 py-2">
          <p class="text-caption text-label-tertiary">必看通过</p>
          <p class="tnum mt-0.5 text-caption font-emphasis text-label">
            {{ approvedRequiredUnitCount }} / {{ snapshot.requiredUnitCount }}
          </p>
        </div>
        <div class="rounded-panel bg-content px-3 py-2">
          <p class="text-caption text-label-tertiary">未处理阻断</p>
          <p class="tnum mt-0.5 text-caption font-emphasis" :class="snapshot.unresolvedBlockerCount > 0 ? 'text-danger' : 'text-success'">
            {{ snapshot.unresolvedBlockerCount }}
          </p>
        </div>
      </section>

      <section class="mt-2 shrink-0 rounded-panel border border-accent/20 bg-accent/5 px-3 py-2.5">
        <div class="flex items-start gap-2">
          <ShieldCheck :size="15" class="mt-0.5 shrink-0 text-accent" />
          <div class="min-w-0 flex-1">
            <p class="text-caption font-emphasis text-label">审核建议</p>
            <p class="mt-0.5 text-caption text-label-secondary">{{ reviewGuidance }}</p>
          </div>
        </div>
        <div v-if="requiredUnits.length > 0" class="mt-2 flex flex-wrap gap-1.5">
          <button
            v-for="unit in requiredUnits"
            :key="unit.id"
            type="button"
            class="inline-flex min-w-0 items-center gap-1.5 rounded-badge border border-danger/20 bg-content px-2 py-1 text-caption text-label transition-colors hover:bg-hover"
            @click="reviewUnit(unit)"
          >
            <span
              class="size-1.5 shrink-0 rounded-full"
              :class="unit.decisionStatus === 'APPROVED'
                ? 'bg-success'
                : unit.decisionStatus === 'REJECTED'
                  ? 'bg-danger'
                  : 'bg-warning'"
            />
            <span class="max-w-64 truncate">{{ unit.title }}</span>
          </button>
        </div>
        <details v-if="optionalUnits.length > 0" class="mt-2">
          <summary class="cursor-pointer text-caption text-label-tertiary">
            可快速略过 {{ optionalUnits.length }} 个单元
          </summary>
          <div class="mt-1.5 flex flex-wrap gap-1.5">
            <button
              v-for="unit in optionalUnits"
              :key="unit.id"
              type="button"
              class="rounded-badge bg-content px-2 py-1 text-caption text-label-secondary hover:bg-hover"
              @click="reviewUnit(unit)"
            >
              {{ unit.title }}
            </button>
          </div>
        </details>
      </section>

      <div class="mt-2 flex min-w-0 items-center gap-3">
        <SectionTabs :model-value="activeTab" :items="tabs" @update:model-value="selectTab" />
        <div class="ml-auto flex shrink-0 items-center gap-2">
          <span v-if="decisionCount > 0" class="text-caption text-label-tertiary">
            {{ decisionCount }} 条审阅结论
          </span>
          <Button v-if="openDecision" size="sm" :disabled="isRegenerating" @click="emit('regenerate')">
            <Icon :as="RefreshCw" size="inline" />重新预审
          </Button>
        </div>
      </div>
      <div
        v-if="isRegenerating"
        class="mt-2 flex shrink-0 items-center gap-2 rounded-panel border border-accent/20 bg-accent/5 px-3 py-2"
      >
        <LoaderCircle :size="14" class="animate-spin text-accent" />
        <span class="text-caption font-emphasis text-label">{{ progress.message }}</span>
        <span class="ml-auto text-caption text-label-tertiary">完成后自动切换到新快照</span>
      </div>

      <div class="mt-2 min-h-0 flex-1">
        <div v-if="activeTab === 'units'" class="grid h-full min-h-0 gap-3 lg:grid-cols-[300px_minmax(0,1fr)]">
          <aside class="flex min-h-0 flex-col rounded-panel border border-separator-soft bg-content">
            <div class="flex shrink-0 items-center gap-2 border-b border-separator-soft px-3 py-2">
              <p class="text-caption font-emphasis text-label">审阅单元</p>
              <button
                type="button"
                class="ml-auto text-caption text-accent hover:underline"
                @click="highRiskOnly = !highRiskOnly"
              >
                {{ highRiskOnly ? '显示全部' : '只看高风险' }}
              </button>
            </div>
            <div class="scroll-area min-h-0 flex-1 overflow-y-auto p-1.5">
              <button
                v-for="unit in units"
                :key="unit.id"
                type="button"
                class="mb-1 flex w-full items-start gap-2 rounded-control px-2 py-2 text-left transition-colors"
                :class="selectedUnit?.id === unit.id ? 'bg-selection' : 'hover:bg-hover'"
                @click="selectUnit(unit)"
              >
                <span
                  class="mt-1 size-2 shrink-0 rounded-full"
                  :class="unitDecisionDot(unit.decisionStatus)"
                />
                <span class="min-w-0 flex-1">
                  <span class="block text-caption font-emphasis text-label">{{ unit.title }}</span>
                  <span class="mt-0.5 block text-caption text-label-tertiary">
                    {{ unit.filePaths.length }} 文件 · {{ unitDecisionLabel(unit.decisionStatus) }}
                  </span>
                </span>
                <span class="flex shrink-0 flex-col items-end gap-1">
                  <Badge :tone="riskTone(unit.riskLevel)">
                    {{ unit.riskLevel === 'HIGH' ? '高' : unit.riskLevel === 'MEDIUM' ? '中' : '低' }}
                  </Badge>
                  <Badge :tone="unitDecisionTone(unit.decisionStatus)">
                    {{ unitDecisionLabel(unit.decisionStatus) }}
                  </Badge>
                </span>
              </button>
            </div>
          </aside>

          <section v-if="selectedUnit" class="scroll-area min-h-0 overflow-y-auto rounded-panel border border-separator-soft bg-content p-4">
            <div class="flex items-start gap-3">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <Badge :tone="riskTone(selectedUnit.riskLevel)">
                    {{ selectedUnit.riskLevel === 'HIGH' ? '高风险' : selectedUnit.riskLevel === 'MEDIUM' ? '中风险' : '低风险' }}
                  </Badge>
                  <Badge v-if="selectedUnit.requiresView" tone="warning">必看</Badge>
                  <Badge v-if="selectedUnit.viewedAt" tone="success">已查看</Badge>
                  <Badge :tone="unitDecisionTone(selectedUnit.decisionStatus)">
                    {{ unitDecisionLabel(selectedUnit.decisionStatus) }}
                  </Badge>
                </div>
                <h3 class="mt-2 text-title font-emphasis text-label">{{ selectedUnit.title }}</h3>
                <div class="mt-3 rounded-panel bg-window px-3 py-2">
                  <p class="text-caption font-emphasis text-label-secondary">关键总结</p>
                  <p class="mt-1 text-caption leading-relaxed text-label">{{ selectedUnit.reason }}</p>
                </div>
                <div class="mt-2 rounded-panel border border-warning/25 bg-warning/5 px-3 py-2">
                  <p class="flex items-center gap-1.5 text-caption font-emphasis text-warning">
                    <MessageSquare :size="13" />需要你确认
                  </p>
                  <p class="mt-1 text-caption leading-relaxed text-label">
                    {{ selectedUnit.decisionQuestion }}
                  </p>
                  <p v-if="selectedUnit.decisionComment" class="mt-2 text-caption text-label-secondary">
                    你的意见：{{ selectedUnit.decisionComment }}
                  </p>
                </div>
              </div>
              <span class="tnum shrink-0 text-caption text-label-tertiary">
                {{ selectedUnit.filePaths.length }} 个文件
              </span>
            </div>

            <div class="mt-4">
              <p class="text-caption font-emphasis text-label-secondary">涉及文件</p>
              <div class="mt-1.5 max-h-36 overflow-y-auto rounded-panel bg-window p-2">
                <p
                  v-for="path in selectedUnit.filePaths"
                  :key="path"
                  class="break-all py-0.5 font-mono text-caption text-label-secondary"
                >
                  {{ path }}
                </p>
              </div>
            </div>

            <div class="mt-4 flex items-center gap-2">
              <Button size="sm" :disabled="selectedUnit.keyHunks.length === 0" @click="showSelectedDiff = !showSelectedDiff">
                <Icon :as="FileDiff" size="inline" />
                {{ showSelectedDiff ? '收起关键 diff' : `查看关键 diff (${selectedUnit.keyHunks.length})` }}
              </Button>
              <span v-if="selectedUnit.viewedAt" class="text-caption text-label-tertiary">
                查看于 {{ formatDateTime(selectedUnit.viewedAt) }}
              </span>
              <Button size="sm" class="ml-auto" @click="codeEditorOpen = true">
                <Icon :as="Code2" size="inline" />打开项目代码
              </Button>
            </div>
            <div v-if="showSelectedDiff" class="mt-3 space-y-3">
              <DiffViewer
                v-for="hunk in selectedUnit.keyHunks"
                :key="`${hunk.filePath}-${hunk.newStart}`"
                :content="hunk.content"
                :scrollable="false"
                class="rounded-panel border border-separator-soft bg-window"
              />
            </div>

            <div class="mt-4 border-t border-separator-soft pt-3">
              <template v-if="openDecision && selectedUnit.decisionStatus === 'PENDING'">
                <div v-if="pendingUnitDecision" class="mb-2">
                  <p class="mb-1 text-caption font-emphasis text-label-secondary">
                    {{ pendingUnitDecision === 'CHANGES_REQUESTED' ? '本项修改要求' : '本项驳回原因' }}
                  </p>
                  <textarea
                    v-model="unitComment"
                    rows="2"
                    maxlength="4000"
                    class="w-full resize-y rounded-panel border border-separator bg-content px-2 py-1.5 text-caption text-label outline-none focus:border-accent"
                  />
                  <div class="mt-2 flex justify-end gap-2">
                    <Button size="sm" :disabled="acting" @click="pendingUnitDecision = null">取消</Button>
                    <Button
                      :variant="pendingUnitDecision === 'REJECTED' ? 'danger' : 'primary'"
                      size="sm"
                      :disabled="acting || !unitComment.trim()"
                      @click="submitUnitDecision(pendingUnitDecision)"
                    >
                      提交本项
                    </Button>
                  </div>
                </div>
                <div v-else class="flex flex-wrap items-center gap-2">
                  <span class="mr-auto text-caption text-label-tertiary">本项决策</span>
                  <Button variant="danger" size="sm" :disabled="acting" @click="beginUnitDecision('REJECTED')">
                    <Icon :as="X" size="inline" />驳回本项
                  </Button>
                  <Button size="sm" :disabled="acting" @click="beginUnitDecision('CHANGES_REQUESTED')">
                    <Icon :as="MessageSquare" size="inline" />本项要求修改
                  </Button>
                  <Button variant="primary" size="sm" :disabled="acting" @click="submitUnitDecision('APPROVED')">
                    <Icon :as="Check" size="inline" />通过本项
                  </Button>
                </div>
              </template>
              <p v-else-if="selectedUnit.decidedAt" class="text-caption text-label-tertiary">
                已由用户决策于 {{ formatDateTime(selectedUnit.decidedAt) }}
              </p>
            </div>
          </section>
        </div>

        <div v-else-if="activeTab === 'findings'" class="scroll-area h-full overflow-y-auto rounded-panel border border-separator-soft bg-content">
          <div v-if="findings.length === 0" class="p-8 text-center text-caption text-label-tertiary">暂无审阅发现</div>
          <article v-for="finding in findings" :key="finding.id" class="border-b border-separator-soft p-4 last:border-b-0">
            <div class="flex items-start gap-3">
              <Badge :tone="findingTone(finding)" class="shrink-0">{{ finding.severity }}</Badge>
              <div class="min-w-0 flex-1">
                <h3 class="text-body font-emphasis text-label">{{ finding.title }}</h3>
                <p class="mt-1 text-caption leading-relaxed text-label-secondary">{{ finding.description }}</p>
                <p v-if="finding.impact" class="mt-1 text-caption text-label-tertiary">影响：{{ finding.impact }}</p>
                <p v-if="finding.filePath" class="mt-2 break-all font-mono text-caption text-label-tertiary">
                  {{ finding.filePath }}<template v-if="finding.lineStart">:{{ finding.lineStart }}</template>
                </p>
                <p v-if="finding.suggestedAction" class="mt-2 text-caption text-accent">
                  建议：{{ finding.suggestedAction }}
                </p>
              </div>
              <Badge tone="neutral" class="shrink-0">{{ findingStatusLabel(finding.status) }}</Badge>
            </div>
            <div v-if="openDecision && finding.status !== 'FIXED'" class="mt-2 flex gap-3 pl-16">
              <button type="button" class="text-caption text-label-tertiary hover:text-label" :disabled="acting" @click="emit('finding-status', finding.id, 'FALSE_POSITIVE')">
                标记误报
              </button>
              <button type="button" class="text-caption text-label-tertiary hover:text-label" :disabled="acting" @click="emit('finding-status', finding.id, 'FIXED')">
                标记已修复
              </button>
            </div>
          </article>
        </div>

        <div v-else-if="activeTab === 'evidence'" class="scroll-area h-full overflow-y-auto rounded-panel border border-separator-soft bg-content">
          <div v-if="evidence.length === 0" class="p-8 text-center text-caption text-label-tertiary">没有可用证据</div>
          <article v-for="item in evidence" :key="item.id" class="flex items-start gap-3 border-b border-separator-soft p-4 last:border-b-0">
            <Badge :tone="evidenceTone(item.status)" class="shrink-0">{{ evidenceStatusLabel(item.status) }}</Badge>
            <div class="min-w-0 flex-1">
              <p class="text-caption font-emphasis text-label">{{ item.type }} · {{ item.title }}</p>
              <p v-if="item.command" class="mt-1 break-all font-mono text-caption text-label-tertiary">{{ item.command }}</p>
              <p v-if="item.summary" class="mt-1 text-caption leading-relaxed text-label-secondary">{{ item.summary }}</p>
              <p class="tnum mt-1 text-caption text-label-tertiary">{{ formatDateTime(item.occurredAt) }}</p>
            </div>
            <Button v-if="item.sourceType === 'TASK_RUN' && item.sourceId" size="sm" @click="openRun(item)">
              <Icon :as="ExternalLink" size="inline" />运行记录
            </Button>
          </article>
        </div>

        <div v-else class="h-full min-h-0 overflow-hidden rounded-panel border border-separator-soft bg-content">
          <div v-if="loadingDiff" class="flex h-40 items-center justify-center gap-2 text-caption text-label-tertiary">
            <Icon :as="FileCode2" size="regular" />正在读取完整 diff…
          </div>
          <div v-else-if="fullDiff !== null" class="flex h-full min-h-0 flex-col">
            <p v-if="fullDiffTruncated" class="border-b border-warning/20 bg-warning/5 px-3 py-1.5 text-caption text-warning">
              diff 正文已截断，哈希仍基于完整内容。
            </p>
            <DiffViewer :content="fullDiff" legend class="min-h-0 flex-1" />
          </div>
          <div v-else class="flex h-40 flex-col items-center justify-center gap-2 text-caption text-label-tertiary">
            <Icon :as="FileCode2" size="xlarge" />
            <Button size="sm" @click="emit('toggle-diff')">加载完整 diff</Button>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div v-if="pendingDecision" class="mr-auto w-full max-w-xl">
        <p class="mb-1 text-caption font-emphasis text-label-secondary">
          {{ pendingDecision === 'CHANGES_REQUESTED' ? '修改意见' : '驳回原因' }}
        </p>
        <textarea
          v-model="comment"
          rows="2"
          maxlength="4000"
          class="w-full resize-y rounded-panel border border-separator bg-content px-2 py-1.5 text-caption text-label outline-none focus:border-accent"
        />
      </div>
      <template v-if="openDecision">
        <Button v-if="pendingDecision" size="sm" :disabled="acting" @click="pendingDecision = null">取消</Button>
        <Button
          v-if="pendingDecision"
          :variant="pendingDecision === 'REJECTED' ? 'danger' : 'primary'"
          size="sm"
          :disabled="acting || comment.trim() === ''"
          @click="submitDecision(pendingDecision, comment)"
        >
          {{ pendingDecision === 'CHANGES_REQUESTED' ? '提交修改意见' : '确认驳回整单' }}
        </Button>
        <template v-else>
          <span v-if="snapshot.canApprove" class="mr-auto text-caption text-success">
            逐项检查已完成，点击“通过整单”后才能提交 Git
          </span>
          <Button variant="danger" size="sm" :disabled="acting" @click="beginDecision('REJECTED')">驳回整单</Button>
          <Button size="sm" :disabled="acting" @click="beginDecision('CHANGES_REQUESTED')">整单要求修改</Button>
          <Button variant="primary" size="sm" :disabled="!snapshot.canApprove || acting" @click="submitDecision('APPROVED')">
            <Icon :as="Check" size="inline" />通过整单
          </Button>
        </template>
      </template>
      <template v-else-if="snapshot.status !== 'COMMITTED' && gitCommitEnabled">
        <span class="mr-auto text-caption text-label-secondary">
          {{ snapshot.status === 'APPROVED' ? '审阅已通过，可继续创建 Git 提交' : '审阅通过后才允许创建 Git 提交' }}
        </span>
        <Button
          v-if="snapshot.status === 'EXPIRED'"
          size="sm"
          :disabled="acting"
          @click="emit('regenerate')"
        >
          <Icon :as="RefreshCw" size="inline" />重新预审
        </Button>
        <Button
          :variant="snapshot.status === 'APPROVED' ? 'primary' : 'default'"
          size="sm"
          :disabled="acting"
          @click="emit('commit-git')"
        >
          <Icon :as="GitBranch" size="inline" />提交 Git
        </Button>
      </template>
      <template v-else-if="snapshot.status === 'COMMITTED'">
        <span class="mr-auto text-caption text-success">
          已提交 {{ snapshot.commitSha?.slice(0, 12) }}
        </span>
      </template>
    </template>
  </Dialog>

  <CodeEditorDialog
    v-if="selectedUnit"
    v-model:open="codeEditorOpen"
    :project-id="snapshot.projectId"
    :relative-path="selectedUnit.filePaths[0] ?? null"
    :line="selectedUnit.keyHunks[0]?.newStart ?? null"
  />
</template>
