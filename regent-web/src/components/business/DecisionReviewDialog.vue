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
 * 审批对话框。批准与驳回共用同一个组件，只是 layout 与校验不同：
 *   批准 —— alert 布局，只确认「要不要做」，主按钮是蓝的
 *   驳回 —— form 布局，必须填理由，理由会进上下文带回给下一轮执行
 *
 * 两种模式都只读传进来的 decision，调用点只管 open / decision / mode 三个东西。
 */
const props = withDefaults(
  defineProps<{
    decision: DecisionSummary | null
    mode: 'approve' | 'reject'
  }>(),
  { decision: null },
)

const emit = defineEmits<{ done: [payload: { mode: 'approve' | 'reject'; note: string }] }>()

const open = defineModel<boolean>('open', { default: false })

const note = ref('')

// 每次打开都清空上一次的输入，否则驳回 A 后打开 B 会带着 A 的理由
watch(open, (value) => {
  if (value) note.value = ''
})

const REJECT_MIN = 6
const canReject = computed(() => note.value.trim().length >= REJECT_MIN)

const confirm = () => {
  const decision = props.decision
  if (!decision) return
  if (props.mode === 'reject' && !canReject.value) return

  // 对话框只负责收集「批还是驳、理由是什么」；真正改状态的是调用点，它要去打接口。
  // 关窗在前、发请求在后：接口慢的时候，用户点完不该对着一个不动的弹框等。
  open.value = false
  emit('done', { mode: props.mode, note: note.value.trim() })
}
</script>

<template>
  <Dialog
    v-model:open="open"
    :layout="mode === 'approve' ? 'alert' : 'form'"
    :icon="mode === 'approve' ? Check : AlertTriangle"
    :icon-tone="mode === 'approve' ? 'default' : 'danger'"
    :title="mode === 'approve' ? '批准并从断点续跑？' : '驳回并终止这条任务'"
    :description="
      mode === 'approve'
        ? '批准后执行器会带着当前上下文继续，不会从头重跑。'
        : '理由会写进决策留痕，也会作为上下文交给下一轮执行。'
    "
  >
    <template v-if="decision">
      <div class="flex items-center gap-2">
        <span class="tnum text-caption text-label-tertiary">#{{ decision.taskId }}</span>
        <DecisionLevelBadge :level="decision.effectiveLevel" />
        <span v-if="decision.levelEscalated" class="text-caption text-label-tertiary">
          AI 自评 {{ decision.aiLevel }}
        </span>
      </div>
      <p class="mt-1.5 text-body text-label">{{ decision.taskTitle }}</p>
      <p class="mt-1 text-caption text-label-secondary">
        命中规则「{{ decision.matchedRuleName ?? '未命中规则' }}」 ·
        {{ formatRelative(decision.raisedAt) }}
      </p>

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
      <Button @click="open = false">取消</Button>
      <Button
        v-if="mode === 'approve'"
        variant="primary"
        @click="confirm"
      >
        批准并续跑
      </Button>
      <Button v-else variant="danger" :disabled="!canReject" @click="confirm">
        确认驳回
      </Button>
    </template>
  </Dialog>
</template>
