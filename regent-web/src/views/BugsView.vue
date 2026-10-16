<script setup lang="ts">
import { ShieldCheck, Wrench } from '@lucide/vue'
import { computed, ref } from 'vue'

import BugSeverityBadge from '@/components/business/BugSeverityBadge.vue'
import BugStatusBadge from '@/components/business/BugStatusBadge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Pagination from '@/components/ui/pagination/Pagination.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import { usePagination } from '@/composables/usePagination'
import { useToast } from '@/composables/useToast'
import { useBugStore } from '@/stores/useBugStore'
import {
  canMarkFixed,
  canRequestFix,
  canVerify,
  canWontFix,
  isResolved,
  type BugSeverity,
  type BugStatus,
} from '@/types/bug'

/*
 * 缺陷单。列表按严重级排，同级新的在前 —— 缺陷不像任务有先后关系，
 * 它的问题永远是「先修哪个」，所以排序要回答这个问题，而不是堆成一团。
 */

const bugStore = useBugStore()
const toast = useToast()

const columns: TableColumn[] = [
  { key: 'id', label: '#', width: '56px', numeric: true },
  { key: 'title', label: '缺陷' },
  { key: 'severity', label: '严重级', width: '88px' },
  { key: 'status', label: '状态', width: '96px' },
  { key: 'source', label: '来源任务', width: '104px' },
  { key: 'reportedAt', label: '发现时间', align: 'right', width: '88px' },
]

// 严重级排序权重。用数组下标而不是另建一张映射表：顺序就是权重，改顺序即改优先级
const severityRank: BugSeverity[] = ['BLOCKER', 'MAJOR', 'MINOR', 'TRIVIAL']

const scope = ref('open')
const scopeOptions = [
  { value: 'all', label: '全部' },
  { value: 'open', label: '未解决' },
  { value: 'closed', label: '已解决' },
]

const keyword = ref('')

const visible = computed(() =>
  [...bugStore.bugs]
    .filter((bug) => {
      if (scope.value === 'open') return !isResolved(bug.status)
      if (scope.value === 'closed') return isResolved(bug.status)
      return true
    })
    .filter((bug) => keyword.value === '' || bug.title.includes(keyword.value))
    .sort(
      (a, b) =>
        severityRank.indexOf(a.severity) - severityRank.indexOf(b.severity) || b.id - a.id,
    ),
)

const { page, pageSize, total, items } = usePagination(visible, {
  pageSize: 20,
  resetOn: [scope, keyword],
})

const selectedId = ref<number | null>(302)
const selected = computed(() => bugStore.bugs.find((b) => b.id === selectedId.value) ?? null)

// 任务标题要另一个上下文的数据，等后端提供「按 id 批量取任务摘要」之后再补。
// 先只显示单号：显示一个猜出来的标题比显示空更糟 —— 那会让人以为关联关系是对的。
const taskLabel = (id: number) => `#${id}`

/*
 * 派发修复任务要同时改 Bug 与 Task 两个聚合，按 AGENTS.md 的约束这属于应用服务的编排：
 * 后端要在一个事务里「新建任务 → 派发 → 回填 bug.fix_task_id」。这条链路还没实现，
 * 所以这里只提示，不做本地假编排 —— 本地改两个 store 的字段看起来能跑，
 * 但刷新就回到原样，而且缺陷单会指向一个数据库里不存在的任务号。
 */
const requestFix = () => {
  const bug = selected.value
  if (!bug) return
  toast.warning('派发修复任务尚未接入', '后端就绪后会新建任务、派发并把任务号回填到缺陷单')
}

const markFixed = () => {
  const bug = selected.value
  if (!bug) return
  bugStore.markFixed(bug.id)
  toast.show(`#${bug.id} 已标记为待验证`, { description: '修复任务跑完不等于问题没了，还要人验一遍' })
}

const verify = () => {
  const bug = selected.value
  if (!bug) return
  bugStore.verify(bug.id)
  toast.success(`#${bug.id} 已关闭`, '验证通过，缺陷单归档')
}

const wontFixOpen = ref(false)
const wontFixReason = ref('')
const WONTFIX_MIN = 6
const canWontFixConfirm = computed(() => wontFixReason.value.trim().length >= WONTFIX_MIN)

const openWontFix = () => {
  wontFixReason.value = ''
  wontFixOpen.value = true
}

const confirmWontFix = () => {
  const bug = selected.value
  if (!bug || !canWontFixConfirm.value) return
  bugStore.wontFix(bug.id, wontFixReason.value.trim())
  wontFixOpen.value = false
  toast.show(`#${bug.id} 已标记为不修复`, { description: '理由留在缺陷单上，将来有人翻到能看见为什么' })
}

// 列表里已经有严重级和状态两列，详情面板不再重复一遍大标题，只补上下文
const context: Record<BugStatus, string> = {
  OPEN: '还没派人修。派发修复任务后会从执行器那拿一条带上下文的任务。',
  FIXING: '修复任务在执行中。跑完之后要人验一遍才能关。',
  FIXED: '修复任务已跑完，但没人验证过。别把它当已经好了。',
  CLOSED: '已验证关闭。',
  WONTFIX: '已判定不修。',
}
</script>

<template>
  <div class="flex h-full min-h-0">
    <div class="flex min-w-0 flex-1 flex-col">
      <div class="flex h-toolbar shrink-0 items-center gap-3 border-b border-separator px-5">
        <h1 class="text-body font-emphasis text-label">Bug 单</h1>
        <span class="tnum text-body text-label-tertiary">
          {{ bugStore.openBugs.length }} 条未解决
        </span>
        <SegmentedControl v-model="scope" :items="scopeOptions" size="lg" class="ml-2" />
        <div class="ml-auto flex items-center gap-2">
          <Input v-model="keyword" placeholder="搜索" size="lg" class="w-52" />
        </div>
      </div>

      <div class="scroll-area min-h-0 flex-1 overflow-auto">
        <Table
          :columns="columns"
          :rows="items"
          :selected-id="selectedId"
          :show-empty="false"
          @select="selectedId = $event.id as number"
        >
          <template #cell="{ row, column }">
            <BugSeverityBadge
              v-if="column.key === 'severity'"
              :severity="row.severity as BugSeverity"
            />
            <BugStatusBadge v-else-if="column.key === 'status'" :status="row.status as BugStatus" />
            <span v-else-if="column.key === 'source'" class="tnum text-label-tertiary">
              #{{ row.sourceTaskId }}
            </span>
            <span v-else-if="column.key === 'reportedAt'" class="tnum text-label-tertiary">
              {{ row.reportedAt }}
            </span>
          </template>
        </Table>

        <EmptyState
          v-if="total === 0"
          title="没有匹配的缺陷"
          :description="
            scope === 'open' ? '执行过程中报出的问题会自动落成 Bug 单' : '处理过的缺陷会留在这'
          "
        />
      </div>

      <Pagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
    </div>

    <aside class="w-72 shrink-0 overflow-y-auto border-l border-separator bg-window p-4">
      <template v-if="selected">
        <div class="flex items-center gap-2">
          <span class="tnum text-caption text-label-tertiary">#{{ selected.id }}</span>
          <BugSeverityBadge :severity="selected.severity" />
          <BugStatusBadge :status="selected.status" class="ml-auto" />
        </div>
        <h2 class="mt-2 text-body font-emphasis text-label">{{ selected.title }}</h2>
        <p class="mt-1 text-caption text-label-secondary">{{ context[selected.status] }}</p>

        <div class="mt-4 rounded-panel border border-separator-soft bg-content p-2.5">
          <p class="text-caption font-emphasis text-label-secondary">复现步骤</p>
          <p class="mt-1 whitespace-pre-line text-caption text-label">
            {{ selected.reproduction }}
          </p>
        </div>

        <div
          v-if="selected.wontFixReason"
          class="mt-4 rounded-panel border border-separator-soft bg-content p-2.5"
        >
          <p class="text-caption font-emphasis text-label-secondary">不修复的理由</p>
          <p class="mt-1 text-caption text-label">{{ selected.wontFixReason }}</p>
        </div>

        <!--
          任务标题在 288px 里放不进「标签在左、值在右」的一行，所以长值改成上下两行，
          短的（时间）仍走一行两列 —— 这是 macOS 检查器的一贯做法，不是随手换的版式。
        -->
        <dl class="mt-4 space-y-3">
          <div>
            <dt class="text-caption text-label-secondary">来源任务</dt>
            <dd class="mt-0.5 text-caption text-label">{{ taskLabel(selected.sourceTaskId) }}</dd>
          </div>
          <div>
            <dt class="text-caption text-label-secondary">修复任务</dt>
            <dd class="mt-0.5 text-caption text-label">
              {{ selected.fixTaskId ? taskLabel(selected.fixTaskId) : '尚未派发' }}
            </dd>
          </div>
          <div class="flex items-center gap-2">
            <dt class="text-caption text-label-secondary">发现时间</dt>
            <dd class="tnum ml-auto text-caption text-label">{{ selected.reportedAt }}</dd>
          </div>
        </dl>

        <div class="mt-4 flex flex-wrap items-center gap-2 border-t border-separator pt-3">
          <Button
            v-if="canRequestFix(selected.status)"
            variant="primary"
            size="sm"
            @click="requestFix"
          >
            <Icon :as="Wrench" size="inline" />派发修复任务
          </Button>
          <Button v-if="canMarkFixed(selected.status)" size="sm" @click="markFixed">
            标记已修复
          </Button>
          <Button
            v-if="canVerify(selected.status)"
            variant="primary"
            size="sm"
            @click="verify"
          >
            <Icon :as="ShieldCheck" size="inline" />验证关闭
          </Button>
          <Button v-if="canWontFix(selected.status)" size="sm" @click="openWontFix">
            不修复
          </Button>
        </div>
      </template>

      <p v-else class="text-caption text-label-tertiary">选中一行查看详情</p>
    </aside>

    <Dialog
      v-model:open="wontFixOpen"
      layout="form"
      title="标记为不修复？"
      description="缺陷单不会消失，理由会留在上面。将来的你（或别人）只能靠这句话判断当初为什么放过了它。"
    >
      <div class="flex items-center gap-2">
        <span class="tnum text-caption text-label-tertiary">#{{ selected?.id }}</span>
        <BugSeverityBadge v-if="selected" :severity="selected.severity" />
      </div>
      <p class="mt-1.5 text-body text-label">{{ selected?.title }}</p>

      <Textarea
        v-model="wontFixReason"
        class="mt-3"
        :rows="3"
        placeholder="例如：这是上游接口的历史遗留行为，改了会连带影响三个月前的对账数据，等下个版本统一处理"
      />
      <p v-if="wontFixReason.length > 0 && !canWontFixConfirm" class="mt-1.5 text-caption text-danger">
        至少 {{ WONTFIX_MIN }} 个字，否则这句话等于没写
      </p>

      <template #footer>
        <Button @click="wontFixOpen = false">取消</Button>
        <Button :disabled="!canWontFixConfirm" @click="confirmWontFix">确认不修复</Button>
      </template>
    </Dialog>
  </div>
</template>
