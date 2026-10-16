<script setup lang="ts">
import {
  ChevronDown,
  ChevronUp,
  Circle,
  CircleCheck,
  ClipboardCheck,
  FileCode,
  FileDiff,
  Lightbulb,
  ListChecks,
  MessageSquare,
  ShieldAlert,
  Terminal,
  TriangleAlert,
} from '@lucide/vue'
import { computed, ref } from 'vue'

import type { RunHumanItem } from '@/components/business/runEvents'
import Badge from '@/components/ui/badge/Badge.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import { formatRelative } from '@/utils/format'

/*
 * 人话视图里的一条过程记录，见 RunDetailDialog。
 *
 * 排版的分寸：执行者的话与最终汇报是主角（正文 13px 主色），命令是技术细节（终端卡片），
 * 思考过程是背景噪音（默认收起，一行标题），会话开始/结束是注脚（居中细线）。
 *
 * 折叠状态放在组件内部：弹框关闭时整棵树会被卸载，重开时每条自动回到初始态，
 * 不需要父组件帮忙重置。
 */
const props = defineProps<{
  item: RunHumanItem
  /** 「谁在说」—— 本机 codex 或外部执行者的自称 */
  speaker: string
}>()

/** 超过这个长度的命令输出/原文默认折叠三行 —— 一条命令输出几万字是常态 */
const FOLD_THRESHOLD = 200

const expanded = ref(false)

const fold = (text: string) => text.length > FOLD_THRESHOLD

const doneCount = computed(() =>
  props.item.kind === 'todo' ? props.item.todos.filter((todo) => todo.completed).length : 0,
)

const decisionText = computed(() => {
  if (props.item.kind !== 'decision') {
    return ''
  }
  const parts: string[] = []
  if (props.item.decisionId !== null) {
    parts.push(`决策 #${props.item.decisionId}`)
  }
  parts.push(decisionStatusLabel(props.item.status))
  if (props.item.status === 'PENDING') {
    parts.push('等你在「决策」页拍板')
  }
  return parts.join(' · ')
})

function decisionStatusLabel(status: string | null) {
  switch (status) {
    case 'PENDING':
      return '待批准'
    case 'APPROVED':
      return '已批准'
    case 'REJECTED':
      return '已驳回'
    default:
      return status ?? '状态未知'
  }
}

function reportLabel(status: string) {
  switch (status) {
    case 'DONE':
      return '已完成'
    case 'FAILED':
      return '失败'
    case 'BLOCKED':
      return '等待决策'
    default:
      return status
  }
}

function reportTone(status: string) {
  switch (status) {
    case 'DONE':
      return 'success' as const
    case 'FAILED':
      return 'danger' as const
    case 'BLOCKED':
      return 'warning' as const
    default:
      return 'neutral' as const
  }
}

function changeKindLabel(kind: string) {
  switch (kind) {
    case 'add':
    case 'create':
      return '新增'
    case 'delete':
    case 'remove':
      return '删除'
    case 'update':
    case 'modify':
      return '修改'
    default:
      return kind
  }
}

function changeKindClass(kind: string) {
  switch (kind) {
    case 'add':
    case 'create':
      return 'text-success'
    case 'delete':
    case 'remove':
      return 'text-danger'
    default:
      return 'text-accent'
  }
}
</script>

<template>
  <!-- 会话开始 / 本轮结束 / 租约回收：注脚，两侧细线把它和过程条目分段 -->
  <div v-if="item.kind === 'note'" class="flex items-center gap-3 text-caption text-label-tertiary">
    <span class="h-px flex-1 bg-separator-soft" />
    <span class="shrink-0">
      {{ item.title }}<template v-if="item.detail"> · <span class="font-mono">{{ item.detail }}</span></template>
    </span>
    <span class="h-px flex-1 bg-separator-soft" />
  </div>

  <template v-else-if="item.kind === 'say'">
    <div class="flex items-center gap-1.5">
      <Icon :as="MessageSquare" size="inline" class="text-accent" />
      <span class="text-caption font-emphasis text-label">{{ speaker }}</span>
      <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">{{ formatRelative(item.at) }}</span>
    </div>
    <p class="mt-1.5 whitespace-pre-wrap text-body text-label">{{ item.text }}</p>
  </template>

  <!-- 执行器的最终汇报：出参 JSON 翻成人话，原始 JSON 见「出参原文」 -->
  <template v-else-if="item.kind === 'report'">
    <div class="flex items-center gap-1.5">
      <Icon :as="ClipboardCheck" size="inline" class="text-accent" />
      <span class="text-caption font-emphasis text-label">{{ speaker }} 汇报结果</span>
      <Badge :tone="reportTone(item.status)">{{ reportLabel(item.status) }}</Badge>
      <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">{{ formatRelative(item.at) }}</span>
    </div>
    <p v-if="item.summary" class="mt-1.5 whitespace-pre-wrap text-body text-label">{{ item.summary }}</p>
    <p v-else class="mt-1.5 text-caption text-label-tertiary">这次汇报没有带摘要。</p>
  </template>

  <!-- 思考过程是背景噪音：默认收起，点「展开」才铺开 -->
  <template v-else-if="item.kind === 'think'">
    <div class="flex items-center gap-1.5">
      <Icon :as="Lightbulb" size="inline" class="text-label-tertiary" />
      <span class="text-caption text-label-tertiary">思考过程</span>
      <button
        type="button"
        class="inline-flex items-center gap-0.5 text-caption text-accent"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : '展开' }}
        <Icon :as="expanded ? ChevronUp : ChevronDown" size="inline" />
      </button>
      <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">{{ formatRelative(item.at) }}</span>
    </div>
    <p v-if="expanded" class="mt-1.5 whitespace-pre-wrap text-caption text-label-tertiary">{{ item.text }}</p>
  </template>

  <template v-else-if="item.kind === 'run'">
    <div class="flex items-center gap-1.5">
      <Icon :as="Terminal" size="inline" class="text-label-secondary" />
      <span class="text-caption font-emphasis text-label-secondary">执行命令</span>
      <Badge v-if="item.running" tone="accent">进行中</Badge>
      <Badge v-else-if="item.exitCode === 0" tone="success">成功</Badge>
      <Badge v-else tone="danger">{{ item.exitCode === null ? '失败' : `退出码 ${item.exitCode}` }}</Badge>
      <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">{{ formatRelative(item.at) }}</span>
    </div>
    <div class="mt-1.5 overflow-hidden rounded-control border border-separator-soft bg-field">
      <pre
        class="whitespace-pre-wrap break-all px-2.5 py-1.5 font-mono text-caption text-label"
      ><span class="select-none text-accent">$ </span>{{ item.command }}</pre>
      <div v-if="item.output" class="border-t border-separator-soft pb-1.5">
        <!-- 48px = 行高 14px × 3 行 + 上 padding 6px。折叠时 pre 的下 padding 必须为 0：
             溢出内容会渲染进下 padding 区，留着它第 4 行就会漏出半行残字 ——
             底部的 6px 留白由外面这层 div 的 pb-1.5 提供 -->
        <pre
          :class="[
            'whitespace-pre-wrap break-all px-2.5 pt-1.5 font-mono text-caption text-label-secondary',
            expanded ? '' : 'max-h-[48px] overflow-hidden',
          ]"
        >{{ item.output }}</pre>
      </div>
    </div>
    <div v-if="item.output && fold(item.output)" class="mt-1 flex">
      <button
        type="button"
        class="ml-auto inline-flex items-center gap-0.5 text-caption text-accent"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : '展开全部' }}
        <Icon :as="expanded ? ChevronUp : ChevronDown" size="inline" />
      </button>
    </div>
    <p v-else-if="!item.output && !item.running" class="mt-1 text-caption text-label-tertiary">无输出</p>
  </template>

  <template v-else-if="item.kind === 'file'">
    <div class="flex items-center gap-1.5">
      <Icon :as="FileDiff" size="inline" class="text-label-secondary" />
      <span class="text-caption font-emphasis text-label-secondary">改动文件</span>
      <Badge v-if="item.running" tone="accent">进行中</Badge>
      <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">{{ formatRelative(item.at) }}</span>
    </div>
    <ul class="mt-1.5 space-y-1">
      <li v-for="change in item.changes" :key="change.path" class="flex items-baseline gap-2 font-mono text-caption">
        <span :class="['w-7 shrink-0', changeKindClass(change.kind)]">{{ changeKindLabel(change.kind) }}</span>
        <span class="truncate text-label-secondary">{{ change.path }}</span>
      </li>
    </ul>
  </template>

  <template v-else-if="item.kind === 'todo'">
    <div class="flex items-center gap-1.5">
      <Icon :as="ListChecks" size="inline" class="text-label-secondary" />
      <span class="text-caption font-emphasis text-label-secondary">待办清单</span>
      <span class="tnum text-caption text-label-tertiary">{{ doneCount }}/{{ item.todos.length }}</span>
      <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">{{ formatRelative(item.at) }}</span>
    </div>
    <ul class="mt-1.5 space-y-1">
      <li v-for="(todo, index) in item.todos" :key="index" class="flex items-baseline gap-1.5 text-caption">
        <Icon
          :as="todo.completed ? CircleCheck : Circle"
          size="inline"
          :class="todo.completed ? 'text-success' : 'text-label-tertiary'"
        />
        <span :class="todo.completed ? 'text-label-tertiary' : 'text-label'">{{ todo.text }}</span>
      </li>
    </ul>
  </template>

  <!-- 要人拍板的事整条包成警示卡：它在过程里的分量比普通条目重 -->
  <template v-else-if="item.kind === 'decision'">
    <div class="rounded-panel border border-warning/40 bg-warning/10 px-2.5 py-2">
      <div class="flex items-center gap-1.5">
        <Icon :as="ShieldAlert" size="inline" class="text-warning" />
        <span class="text-caption font-emphasis text-warning">
          需要人工决策{{ item.level ? ` · ${item.level}` : '' }}
        </span>
        <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">{{ formatRelative(item.at) }}</span>
      </div>
      <p class="mt-1 text-caption text-label-secondary">{{ decisionText }}</p>
    </div>
  </template>

  <template v-else-if="item.kind === 'error'">
    <div class="rounded-panel border border-danger/40 bg-danger/10 px-2.5 py-2">
      <div class="flex items-center gap-1.5">
        <Icon :as="TriangleAlert" size="inline" class="text-danger" />
        <span class="text-caption font-emphasis text-danger">执行出错</span>
        <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">{{ formatRelative(item.at) }}</span>
      </div>
      <p class="mt-1 font-mono text-caption text-danger">{{ item.name }}</p>
    </div>
  </template>

  <template v-else>
    <div class="flex items-center gap-1.5">
      <Icon :as="FileCode" size="inline" class="text-label-tertiary" />
      <span class="text-caption text-label-tertiary">其他事件</span>
      <button
        v-if="fold(item.text)"
        type="button"
        class="inline-flex items-center gap-0.5 text-caption text-accent"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : '展开' }}
        <Icon :as="expanded ? ChevronUp : ChevronDown" size="inline" />
      </button>
      <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">{{ formatRelative(item.at) }}</span>
    </div>
    <div class="mt-1.5">
      <pre
        :class="[
          'whitespace-pre-wrap break-all font-mono text-caption text-label-secondary',
          expanded ? '' : 'max-h-[42px] overflow-hidden',
        ]"
      >{{ item.text }}</pre>
    </div>
  </template>
</template>
