<script setup lang="ts">
import { AlertTriangle, Check } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Button from '@/components/ui/button/Button.vue'
import type { DecisionSummary } from '@/types/api'
import { formatRelative } from '@/utils/format'

/*
 * 审批对话框。查看、批准、驳回共用同一个组件：
 *   查看 —— 展示判断所需的完整事实；待批记录可直接切换到批准或驳回
 *   批准 —— 确认从断点续跑，主按钮是蓝的
 *   驳回 —— 必须填理由，理由会进上下文带回给下一轮执行
 *
 * 三种模式都只读传进来的 decision，调用点只管 open / decision / mode 三个东西。
 */
const props = withDefaults(
  defineProps<{
    decision: DecisionSummary | null
    mode: 'view' | 'approve' | 'reject'
  }>(),
  { decision: null, mode: 'view' },
)

const emit = defineEmits<{
  done: [payload: { mode: 'approve' | 'reject'; note: string }]
  review: [mode: 'approve' | 'reject']
}>()

const open = defineModel<boolean>('open', { default: false })

const note = ref('')

// 每次打开都清空上一次的输入，否则驳回 A 后打开 B 会带着 A 的理由
watch(open, (value) => {
  if (value) note.value = ''
})

const REJECT_MIN = 6
const canReject = computed(() => note.value.trim().length >= REJECT_MIN)
const isPending = computed(() => props.decision?.status === 'PENDING')

const title = computed(() => {
  if (props.mode === 'approve') return '批准并从断点续跑？'
  if (props.mode === 'reject') return '驳回并终止这条任务'
  return props.decision ? `决策 #${props.decision.id}` : '决策详情'
})

const description = computed(() => {
  if (props.mode === 'approve') return '批准后执行器会带着当前上下文继续，不会从头重跑。'
  if (props.mode === 'reject') return '理由会写进决策留痕，也会作为上下文交给下一轮执行。'
  if (!isPending.value) return '这项决策已经处理，下面是当时的判断依据与处理结果。'
  return '先核对完整问题和影响范围，再决定是否放行。'
})

const resolvedByText = (by: string | null) => (by === 'AI' ? 'AI' : '人工')

const outcomeText = (status: DecisionSummary['status']) => {
  if (status === 'APPROVED') return '已批准'
  if (status === 'REJECTED') return '已驳回'
  if (status === 'AUTO_RESOLVED') return 'AI 自主决断'
  return '待批准'
}

const confirm = () => {
  const decision = props.decision
  const mode = props.mode
  if (!decision || mode === 'view') return
  if (mode === 'reject' && !canReject.value) return

  // 对话框只负责收集「批还是驳、理由是什么」；真正改状态的是调用点，它要去打接口。
  // 关窗在前、发请求在后：接口慢的时候，用户点完不该对着一个不动的弹框等。
  open.value = false
  emit('done', { mode, note: note.value.trim() })
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="log"
    :icon="mode === 'approve' ? Check : AlertTriangle"
    :icon-tone="mode === 'reject' ? 'danger' : 'default'"
    :title="title"
    :description="description"
  >
    <template v-if="decision">
      <div class="flex flex-wrap items-center gap-2">
        <span class="tnum text-caption text-label-tertiary">决策 #{{ decision.id }}</span>
        <span class="tnum text-caption text-label-tertiary">#{{ decision.taskId }}</span>
        <DecisionLevelBadge :level="decision.effectiveLevel" />
        <span v-if="decision.levelEscalated" class="text-caption text-label-tertiary">
          AI 自评 {{ decision.aiLevel }}
        </span>
        <span class="text-caption text-label-tertiary">{{ decision.projectName }}</span>
        <span class="tnum ml-auto text-caption text-label-tertiary">
          {{ formatRelative(decision.raisedAt) }}提出
        </span>
      </div>

      <div
        class="mt-3 max-h-[55vh] overflow-y-auto rounded-panel border border-separator-soft bg-content p-3"
      >
        <p class="text-caption text-label-tertiary">任务</p>
        <p class="mt-1 text-body font-emphasis text-label">{{ decision.taskTitle }}</p>

        <div class="mt-4 border-t border-separator-soft pt-3">
          <p class="text-caption font-emphasis text-label-secondary">待决问题</p>
          <p class="mt-1 whitespace-pre-wrap break-words text-body text-label">
            {{ decision.question }}
          </p>
        </div>

        <div class="mt-4 border-t border-separator-soft pt-3">
          <p class="text-caption font-emphasis text-label-secondary">候选方案</p>
          <ol v-if="decision.options.length > 0" class="mt-1.5 space-y-1.5">
            <li
              v-for="(option, index) in decision.options"
              :key="`${index}-${option}`"
              class="flex gap-2 text-caption text-label"
            >
              <span class="tnum shrink-0 text-label-tertiary">{{ index + 1 }}.</span>
              <span class="break-words">{{ option }}</span>
            </li>
          </ol>
          <p v-else class="mt-1 text-caption text-label-tertiary">执行器未提供候选方案。</p>
        </div>

        <div class="mt-4 border-t border-separator-soft pt-3">
          <p class="text-caption font-emphasis text-label-secondary">AI 建议</p>
          <p class="mt-1 whitespace-pre-wrap break-words text-caption text-label">
            {{ decision.recommendation ?? '执行器未提供建议。' }}
          </p>
        </div>

        <div class="mt-4 border-t border-separator-soft pt-3">
          <p class="text-caption font-emphasis text-label-secondary">影响判断</p>
          <p class="mt-1 text-caption text-label">
            {{ decision.reversible ? '可逆改动' : '不可逆改动' }}
            <span v-if="decision.blastRadius"> · {{ decision.blastRadius }}</span>
          </p>
        </div>

        <div class="mt-4 border-t border-separator-soft pt-3">
          <p class="text-caption font-emphasis text-label-secondary">分级依据</p>
          <p class="mt-1 text-caption text-label">
            命中规则「{{ decision.matchedRuleName ?? '未命中规则' }}」
            <template v-if="decision.levelEscalated">
              · AI 自评 {{ decision.aiLevel }}，最终按 {{ decision.effectiveLevel }} 处理
            </template>
          </p>
        </div>

        <div
          v-if="decision.status !== 'PENDING'"
          class="mt-4 border-t border-separator-soft pt-3"
        >
          <p class="text-caption font-emphasis text-label-secondary">处理结果</p>
          <p class="mt-1 text-caption text-label">
            {{ outcomeText(decision.status) }} · {{ resolvedByText(decision.resolvedBy) }}
            <template v-if="decision.resolvedAt">
              · {{ formatRelative(decision.resolvedAt) }}
            </template>
          </p>
          <p v-if="decision.resolvedChoice" class="mt-1 text-caption text-label-secondary">
            选择：{{ decision.resolvedChoice }}
          </p>
          <p class="mt-1 whitespace-pre-wrap break-words text-caption text-label-secondary">
            {{ decision.resolutionNote ?? '没有留下说明。' }}
          </p>
        </div>
      </div>

      <Textarea
        v-if="mode === 'reject'"
        v-model="note"
        class="mt-3"
        :rows="3"
        placeholder="例如：这个改动会影响线上数据，必须人工确认后再执行"
      />
      <!-- 提示只在人开始写之后才出现：一打开就飘红，等于先把人骂一顿 -->
      <p v-if="mode === 'reject' && note.length > 0 && !canReject" class="mt-1.5 text-caption text-danger">
        至少 {{ REJECT_MIN }} 个字，否则下一轮执行拿不到可用的信息
      </p>
    </template>

    <template #footer>
      <Button @click="open = false">{{ mode === 'view' ? '关闭' : '取消' }}</Button>
      <template v-if="mode === 'view' && isPending">
        <Button @click="emit('review', 'reject')">驳回</Button>
        <Button variant="primary" @click="emit('review', 'approve')">批准并续跑</Button>
      </template>
      <Button
        v-if="mode === 'approve'"
        variant="primary"
        @click="confirm"
      >
        批准并续跑
      </Button>
      <Button
        v-else-if="mode === 'reject'"
        variant="danger"
        :disabled="!canReject"
        @click="confirm"
      >
        确认驳回
      </Button>
    </template>
  </Dialog>
</template>
