<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import DecisionReviewDialog from '@/components/business/DecisionReviewDialog.vue'
import Button from '@/components/ui/button/Button.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Pagination from '@/components/ui/pagination/Pagination.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useDecisionStore, type DecisionScope } from '@/stores/useDecisionStore'
import type { DecisionSummary } from '@/types/api'
import { formatRelative } from '@/utils/format'

/*
 * 决策页的取舍：只放「需要人拍板」的和「刚拍过板」的。
 *
 * L0/L1 的自主决断不在这里出现 —— 它们只留痕，不该来打扰人。把自主决断也堆进来的话，
 * 真正待批的那两条会被淹掉，人就会开始无脑点批准，分级决策机制也就白做了。
 */

const store = useDecisionStore()
const toast = useToast()

const columns: TableColumn[] = [
  { key: 'taskId', label: '任务', width: '72px', numeric: true },
  { key: 'level', label: '等级', width: '168px', hideBelow: 'md' },
  { key: 'rule', label: '命中规则', width: '128px', hideBelow: 'lg' },
  { key: 'question', label: '待决问题' },
  { key: 'raisedAt', label: '提出时间', align: 'right', width: '88px', hideBelow: 'md' },
  { key: 'actions', label: '', align: 'right', width: '128px' },
]

// SegmentedControl 的 model 是 string，这里换算成后端的 scope 取值
const scope = computed({
  get: () => store.scope.toLowerCase(),
  set: (value: string) => {
    store.scope = value.toUpperCase() as DecisionScope
  },
})

const scopeOptions = [
  { value: 'pending', label: '待批准' },
  { value: 'resolved', label: '已处理' },
]

onMounted(() => {
  void store.load()
  void store.loadPendingCount()
})

const reviewOpen = ref(false)
const reviewMode = ref<'view' | 'approve' | 'reject'>('view')
const active = ref<DecisionSummary | null>(null)

const openReview = (decision: DecisionSummary, mode: 'view' | 'approve' | 'reject') => {
  active.value = decision
  reviewMode.value = mode
  reviewOpen.value = true
}

const onReviewed = async (payload: { mode: 'approve' | 'reject'; note: string }) => {
  const decision = active.value
  if (!decision) return
  // 先拿住引用再清空：接口返回前 active 可能已被置空，那时再读就是 undefined
  const taskId = decision.taskId
  const decisionId = decision.id
  active.value = null

  const failure = await store.review(decisionId, payload.mode, payload.note)
  if (failure) {
    toast.warning(`决策 #${decisionId} 没能${payload.mode === 'approve' ? '批准' : '驳回'}`, failure)
    return
  }
  if (payload.mode === 'approve') {
    toast.success(`任务 #${taskId} 已批准`, '执行器从断点续跑，上下文里带着你的决定')
  } else {
    toast.show(`任务 #${taskId} 已驳回，任务终止`, { description: '理由已写入决策留痕' })
  }
}

const outcomeText = (status: DecisionSummary['status']) => {
  if (status === 'APPROVED') return '已批准'
  if (status === 'REJECTED') return '已驳回'
  return 'AI 自主决断'
}

const resolvedByText = (by: string | null) => (by === 'AI' ? 'AI' : '人工')
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="flex h-toolbar shrink-0 items-center gap-3 border-b border-separator px-3 lg:px-5">
      <h1 class="text-body font-emphasis text-label">决策</h1>
      <span class="tnum text-body text-label-tertiary">
        {{ store.pendingTotal }} 条待批
      </span>
      <SegmentedControl v-model="scope" :items="scopeOptions" size="lg" class="lg:ml-2" />
      <p class="ml-auto hidden text-caption text-label-tertiary lg:block">
        自主决断（L0/L1）只留痕，不在这里打扰你
      </p>
    </div>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <Table
        :columns="columns"
        :rows="store.items"
        row-key="id"
        :show-empty="false"
        @select="openReview($event, 'view')"
      >
        <template #cell="{ row, column }">
          <span v-if="column.key === 'taskId'" class="tnum text-label-tertiary">
            #{{ row.taskId }}
          </span>

          <span v-else-if="column.key === 'level'" class="inline-flex items-center gap-1.5">
            <DecisionLevelBadge :level="row.effectiveLevel" />
            <!-- 自评与最终等级不一致 = 规则把它抬上去了，这是要给人看的关键信息 -->
            <span v-if="row.levelEscalated" class="text-caption text-label-tertiary">
              AI 自评 {{ row.aiLevel }}
            </span>
          </span>

          <span v-else-if="column.key === 'rule'" class="text-label-secondary">
            {{ row.matchedRuleName ?? '—' }}
          </span>

          <span v-else-if="column.key === 'question'" :title="row.question" class="block truncate">
            <span>{{ row.taskTitle }}</span>
            <span class="text-label-tertiary"> · {{ row.question }}</span>
          </span>

          <span v-else-if="column.key === 'raisedAt'" class="tnum text-label-tertiary">
            {{ formatRelative(row.raisedAt) }}
          </span>

          <span v-else-if="column.key === 'actions'" class="flex justify-end gap-1.5">
            <template v-if="row.status === 'PENDING'">
              <Button size="sm" @click.stop="openReview(row, 'reject')">驳回</Button>
              <Button size="sm" variant="primary" @click.stop="openReview(row, 'approve')">
                批准
              </Button>
            </template>
            <!-- 已处理的决策把「谁拍的板」和「理由」一并留在行里：这几行就是这个系统的审计记录，
                 光有结论没有理由的话，下一轮执行与人复盘时都只能靠猜 -->
            <Tooltip v-else :content="row.resolutionNote ?? '这条决策没有留下说明'">
              <span class="cursor-default text-caption text-label-tertiary">
                {{ outcomeText(row.status) }} · {{ resolvedByText(row.resolvedBy) }}
              </span>
            </Tooltip>
          </span>
        </template>
      </Table>

      <EmptyState
        v-if="store.loading && store.items.length === 0"
        title="正在加载…"
        description="从后端读取决策列表"
      />
      <EmptyState v-else-if="store.error" title="加载失败" :description="store.error" />
      <EmptyState
        v-else-if="store.total === 0"
        :title="store.scope === 'PENDING' ? '没有待批决策' : '还没有处理过决策'"
        :description="
          store.scope === 'PENDING'
            ? '任务遇到需要拍板的地方才会出现在这里'
            : '批准或驳回之后，记录会留在这'
        "
      />
    </div>

    <Pagination v-model:page="store.page" v-model:page-size="store.pageSize" :total="store.total" />

    <DecisionReviewDialog
      v-model:open="reviewOpen"
      :decision="active"
      :mode="reviewMode"
      @done="onReviewed"
      @review="reviewMode = $event"
    />
  </div>
</template>
