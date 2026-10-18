<script setup lang="ts">
import { RefreshCw, Search, X } from '@lucide/vue'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import DecisionReviewDialog from '@/components/business/DecisionReviewDialog.vue'
import ImageAttachmentsDialog from '@/components/business/ImageAttachmentsDialog.vue'
import Button from '@/components/ui/button/Button.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import Pagination from '@/components/ui/pagination/Pagination.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useDecisionStore, type DecisionScope } from '@/stores/useDecisionStore'
import type { DecisionSummary, ImageAttachment } from '@/types/api'
import { formatDateTime, formatRelative } from '@/utils/format'

/*
 * 决策页的取舍：只放「需要人拍板」的和「刚拍过板」的。
 *
 * L0/L1 的自主决断不在这里出现 —— 它们只留痕，不该来打扰人。把自主决断也堆进来的话，
 * 真正待批的那两条会被淹掉，人就会开始无脑点批准，分级决策机制也就白做了。
 */

const store = useDecisionStore()
const toast = useToast()

const columns: TableColumn[] = [
  // 任务号、任务标题、待决问题合成一列：它们共同回答「这一行在等什么」
  { key: 'question', label: '待决事项' },
  { key: 'level', label: '等级', width: '56px', hideBelow: 'md' },
  { key: 'rule', label: '命中规则', width: '112px', hideBelow: 'xl' },
  { key: 'raisedAt', label: '提出时间', align: 'right', width: '80px', hideBelow: 'xl' },
  { key: 'actions', label: '', align: 'right', width: '132px' },
]

// SegmentedControl 的 model 是 string，这里换算成后端的 scope 取值
const scope = computed({
  get: () => store.scope.toLowerCase(),
  set: (value: string) => {
    store.scope = value.toUpperCase() as DecisionScope
    store.page = 1
  },
})

const keywordInput = ref(store.keyword)
let keywordTimer: number | undefined
watch(keywordInput, (value) => {
  window.clearTimeout(keywordTimer)
  keywordTimer = window.setTimeout(() => {
    store.keyword = value.trim()
    store.page = 1
  }, 250)
})

const clearKeyword = () => {
  window.clearTimeout(keywordTimer)
  keywordInput.value = ''
  store.keyword = ''
  store.page = 1
}

const scopeOptions = [
  { value: 'pending', label: '待批准' },
  { value: 'resolved', label: '已处理' },
]

onMounted(() => {
  void store.load()
  void store.loadPendingCount()
})

onUnmounted(() => window.clearTimeout(keywordTimer))

const reload = () => Promise.all([store.load(), store.loadPendingCount()])

const reviewOpen = ref(false)
const reviewMode = ref<'view' | 'approve' | 'reject'>('view')
const active = ref<DecisionSummary | null>(null)
const previewOpen = ref(false)
const previewAttachment = ref<ImageAttachment | null>(null)

const openReview = (decision: DecisionSummary, mode: 'view' | 'approve' | 'reject') => {
  active.value = decision
  reviewMode.value = mode
  reviewOpen.value = true
}

const openPreview = (attachment: ImageAttachment) => {
  previewAttachment.value = attachment
  previewOpen.value = true
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
    toast.success(`任务 #${taskId} 已批准`, '任务已放行；已有执行会话时会从断点续跑')
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
    <ListToolbar>
      <template #title>
        <h1 class="text-body font-emphasis text-label">决策</h1>
        <span class="tnum text-body text-label-tertiary">
          {{ store.pendingTotal }} 条待批
        </span>
      </template>
      <template #filters>
        <div class="relative min-w-0 flex-1 sm:w-52 sm:flex-none">
          <Icon
            :as="Search"
            size="inline"
            class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-tertiary"
          />
          <Input
            v-model="keywordInput"
            size="lg"
            placeholder="搜索任务标题或待决问题"
            aria-label="搜索决策"
            class="pl-7 pr-7"
          />
          <button
            v-if="keywordInput"
            type="button"
            class="absolute right-1 top-1/2 flex size-5 -translate-y-1/2 items-center justify-center rounded-control text-label-tertiary hover:bg-hover hover:text-label"
            aria-label="清空决策搜索"
            @click="clearKeyword"
          >
            <Icon :as="X" size="inline" />
          </button>
        </div>
        <SegmentedControl v-model="scope" :items="scopeOptions" size="lg" />
        <p class="hidden text-caption text-label-tertiary lg:block">
          自主决断（L0/L1）只留痕，不在这里打扰你
        </p>
      </template>
      <template #actions>
        <Tooltip content="刷新决策列表">
          <IconButton label="刷新决策列表" size="lg" :disabled="store.loading" @click="reload">
            <Icon :as="RefreshCw" size="large" :class="store.loading ? 'animate-spin' : ''" />
          </IconButton>
        </Tooltip>
      </template>
    </ListToolbar>

    <div class="scroll-area min-h-0 flex-1 overflow-auto">
      <Table
        :columns="columns"
        :rows="store.items"
        row-key="id"
        :show-empty="false"
        touch-rows
        @select="openReview($event, 'view')"
      >
        <template #cell="{ row, column }">
          <span v-if="column.key === 'question'" class="flex min-w-0 items-center gap-2">
            <RouterLink
              :to="{ name: 'tasks', query: { taskId: String(row.taskId) } }"
              class="tnum shrink-0 rounded-sm text-caption text-label-tertiary transition-colors duration-100 ease-apple hover:text-accent hover:underline hover:underline-offset-2"
              :aria-label="`在任务页查看任务 #${row.taskId}`"
              @click.stop
            >
              #{{ row.taskId }}
            </RouterLink>
            <span class="truncate" :title="`${row.taskTitle} · ${row.question}`">
              <span class="font-emphasis text-label">{{ row.taskTitle }}</span>
              <span class="text-label-tertiary"> · {{ row.question }}</span>
            </span>
          </span>

          <span
            v-else-if="column.key === 'level'"
            class="inline-flex items-center gap-1"
            :title="
              row.levelEscalated
                ? `AI 自评 ${row.aiLevel}，规则仲裁后按 ${row.effectiveLevel} 处理`
                : undefined
            "
          >
            <DecisionLevelBadge :level="row.effectiveLevel" compact />
            <!-- 自评与最终等级不一致 = 规则把它抬上去了，这是要给人看的关键信息 -->
            <span v-if="row.levelEscalated" class="text-caption text-warning" aria-hidden="true">↑</span>
            <span v-if="row.levelEscalated" class="sr-only">
              规则抬级，AI 自评 {{ row.aiLevel }}
            </span>
          </span>

          <span
            v-else-if="column.key === 'rule'"
            class="truncate text-caption text-label-tertiary"
            :title="row.matchedRuleName ?? undefined"
          >
            {{ row.matchedRuleName ?? '—' }}
          </span>

          <span
            v-else-if="column.key === 'raisedAt'"
            class="tnum text-caption text-label-tertiary"
            :title="formatDateTime(row.raisedAt)"
          >
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
      <EmptyState v-else-if="store.error" title="加载失败" :description="store.error">
        <Button size="sm" :disabled="store.loading" @click="reload">重新加载</Button>
      </EmptyState>
      <EmptyState
        v-else-if="store.total === 0 && store.keyword"
        title="没有匹配的决策"
        description="换个任务标题或待决问题关键词再试。"
      >
        <Button size="sm" @click="clearKeyword">清除搜索</Button>
      </EmptyState>
      <EmptyState
        v-else-if="store.total === 0"
        :title="store.scope === 'PENDING' ? '没有待批决策' : '还没有处理过决策'"
        :description="
          store.scope === 'PENDING'
            ? '任务遇到需要拍板的地方才会出现在这里'
            : '批准或驳回之后，记录会留在这'
        "
      >
        <Button size="sm" @click="scope = store.scope === 'PENDING' ? 'resolved' : 'pending'">
          {{ store.scope === 'PENDING' ? '查看已处理' : '查看待批准' }}
        </Button>
      </EmptyState>
    </div>

    <Pagination v-model:page="store.page" v-model:page-size="store.pageSize" :total="store.total" />

    <DecisionReviewDialog
      v-model:open="reviewOpen"
      :decision="active"
      :mode="reviewMode"
      @done="onReviewed"
      @review="reviewMode = $event"
      @preview="openPreview"
    />
    <ImageAttachmentsDialog
      v-model:open="previewOpen"
      :attachments="previewAttachment ? [previewAttachment] : []"
    />
  </div>
</template>
