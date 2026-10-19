<script setup lang="ts">
import {
  AlertTriangle,
  Bell,
  Download,
  ListChecks,
  MoreHorizontal,
  Plus,
  Settings,
  Trash2,
} from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'

import BugSeverityBadge from '@/components/business/BugSeverityBadge.vue'
import BugStatusBadge from '@/components/business/BugStatusBadge.vue'
import DecisionLevelBadge from '@/components/business/DecisionLevelBadge.vue'
import TaskStatusBadge from '@/components/business/TaskStatusBadge.vue'
import Avatar from '@/components/ui/avatar/Avatar.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogPanel from '@/components/ui/dialog/DialogPanel.vue'
import { dialogDescriptionClass, dialogTitleClass } from '@/components/ui/dialog/dialog.variants'
import DropdownMenu from '@/components/ui/dropdown-menu/DropdownMenu.vue'
import DropdownMenuItem from '@/components/ui/dropdown-menu/DropdownMenuItem.vue'
import DropdownMenuSeparator from '@/components/ui/dropdown-menu/DropdownMenuSeparator.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import Pagination from '@/components/ui/pagination/Pagination.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Popover from '@/components/ui/popover/Popover.vue'
import Progress from '@/components/ui/progress/Progress.vue'
import RadioGroup from '@/components/ui/radio/RadioGroup.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Select from '@/components/ui/select/Select.vue'
import Skeleton from '@/components/ui/skeleton/Skeleton.vue'
import StatusDot from '@/components/ui/status-dot/StatusDot.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import type { TaskStatus } from '@/types/task'
import BaselineSection from '@/views/BaselineSection.vue'
import FontProbe from '@/views/FontProbe.vue'

const text = ref('给 app_audit 增加导出功能')
const emptyText = ref('')
const invalidText = ref('不是合法的邮箱')
const pickOne = ref('b')
const checked = ref(true)
const enabled = ref(true)
const autoRun = ref(false)

const toast = useToast()

// 分页样板块：给一个大总数，让翻页按钮真的能点
const demoPage = ref(3)
const demoPageSize = ref(20)

const filter = ref('running')
const filterOptions = [
  { value: 'all', label: '全部' },
  { value: 'running', label: '执行中' },
  { value: 'blocked', label: '待批准' },
  { value: 'done', label: '已完成' },
]

const level = ref('L2')
const levelOptions = [
  { value: 'L0', label: 'L0' },
  { value: 'L1', label: 'L1' },
  { value: 'L2', label: 'L2' },
  { value: 'L3', label: 'L3' },
]

const project = ref('app_audit')
const projects = [
  { value: 'app_audit', label: 'app_audit' },
  { value: 'safe-guard', label: 'safe-guard' },
]

const tableColumns: TableColumn[] = [
  { key: 'selection', label: '', width: '36px' },
  { key: 'id', label: '#', width: '52px' },
  { key: 'requirement', label: '需求' },
  { key: 'level', label: '等级', width: '92px' },
  { key: 'status', label: '状态', width: '92px' },
  { key: 'duration', label: '耗时', align: 'right', width: '72px' },
  { key: 'tokens', label: 'Token', align: 'right', width: '78px' },
  { key: 'actions', label: '', align: 'right', width: '40px' },
]

type BaselineTableRow = {
  id: number
  requirement: string
  level: 'L0' | 'L1' | 'L2' | 'L3'
  status: TaskStatus
  duration: string
  tokens: number
}

const tableRows: BaselineTableRow[] = [
  {
    id: 1024,
    requirement: '给 app_audit 增加导出功能',
    level: 'L0',
    status: 'DONE',
    duration: '2m 14s',
    tokens: 12840,
  },
  {
    id: 1025,
    requirement: '修正登录页空指针',
    level: 'L1',
    status: 'RUNNING',
    duration: '00:48',
    tokens: 4310,
  },
  {
    id: 1026,
    requirement: '调整 deploy 目录下的发布脚本，并同步更新服务器上的 systemd 单元',
    level: 'L3',
    status: 'BLOCKED',
    duration: '—',
    tokens: 980,
  },
  {
    id: 1027,
    requirement: '给订单表增加字段',
    level: 'L2',
    status: 'BLOCKED',
    duration: '—',
    tokens: 2210,
  },
  {
    id: 1028,
    requirement: '修复附件上传后偶发丢帧',
    level: 'L1',
    status: 'FAILED',
    duration: '12s',
    tokens: 6104,
  },
]

const selectedRow = ref<string | number | null>(1025)
const checkedTableRows = ref<number[]>([1025])
const tableSelectionState = computed<boolean | 'indeterminate'>(() => {
  if (checkedTableRows.value.length === 0) return false
  if (checkedTableRows.value.length === tableRows.length) return true
  return 'indeterminate'
})

const setTableRowChecked = (id: number, checked: boolean) => {
  checkedTableRows.value = checked
    ? [...new Set([...checkedTableRows.value, id])]
    : checkedTableRows.value.filter((rowId) => rowId !== id)
}

const toggleAllTableRows = (checked: boolean) => {
  checkedTableRows.value = checked ? tableRows.map((row) => row.id) : []
}

const dialogOpen = ref(false)
const rejectReason = ref('')

// 尺寸实测：把真实渲染出来的高度打印出来，避免「凭感觉调像素」
const measures = ref<{ name: string; height: number; width: number }[]>([])
onMounted(() => {
  measures.value = [...document.querySelectorAll<HTMLElement>('[data-measure]')].map((node) => {
    const rect = node.getBoundingClientRect()
    return {
      name: node.dataset.measure ?? '',
      height: Math.round(rect.height * 10) / 10,
      width: Math.round(rect.width),
    }
  })
})

const iconSizes = ['inline', 'regular', 'large', 'xlarge'] as const
const iconStrokes = [1.5, 1.75, 2, 2.25]
const fontWeights = [
  { value: 400, name: 'PingFang Regular' },
  { value: 500, name: 'PingFang Medium' },
  { value: 600, name: 'PingFang Semibold' },
] as const

// 颜色样本用显式类名，不能拼 `bg-${token}` —— Tailwind 只认源码里出现的字面量
const colorTokens = [
  { label: 'accent', class: 'bg-accent' },
  { label: 'success', class: 'bg-success' },
  { label: 'warning', class: 'bg-warning' },
  { label: 'danger', class: 'bg-danger' },
  { label: 'neutral', class: 'bg-neutral' },
  { label: 'selection', class: 'bg-selection' },
]
</script>

<template>
  <!-- 基准页自己铺灰底：白色面板要落在灰底上才读得出「面板」，这是系统设置的做法 -->
  <div class="min-h-full bg-window py-6">
   <div class="mx-auto max-w-4xl px-5">
    <h1 class="text-metric font-emphasis text-label">样式基准页</h1>
    <p class="mt-1 text-caption text-label-secondary">
      这一页的唯一目的：让你肉眼确认「像不像 macOS」。深浅两套并存，不靠描述、不靠截图。
    </p>

    <div class="mt-6">
      <BaselineSection title="尺寸实测" hint="页面上真实渲染出来的高度，不是文档里写的值">
        <div class="flex flex-wrap gap-x-8 gap-y-3">
          <div class="flex items-center gap-2">
            <Button data-measure="Button regular">默认</Button>
            <Input v-model="text" class="w-32" data-measure="Input regular" />
            <Select v-model="project" :items="projects" class="w-28" data-measure="Select regular" />
          </div>
          <div class="flex items-center gap-2">
            <Button size="sm" data-measure="Button sm">小</Button>
            <Button size="lg" data-measure="Button lg">大</Button>
            <Checkbox v-model="checked" data-measure="Checkbox" />
            <Switch v-model="enabled" data-measure="Switch" />
          </div>
        </div>
        <table class="mt-3 text-caption">
          <tbody>
            <tr v-for="item in measures" :key="item.name">
              <td class="pr-4 text-label-secondary">{{ item.name }}</td>
              <td class="tnum pr-4 text-right text-label">{{ item.height }}px</td>
              <td class="tnum text-right text-label-tertiary">{{ item.width }}px 宽</td>
            </tr>
          </tbody>
        </table>
      </BaselineSection>

      <BaselineSection
        title="字体探针"
        hint="canvas 像素指纹比对，回答「浏览器实际用了哪套字形」，不靠肉眼猜"
      >
        <FontProbe />
      </BaselineSection>

      <BaselineSection title="图标尺寸" hint="图标由控件尺寸派生，不与 13px 文字等高">
        <div class="space-y-3">
          <div
            v-for="size in iconSizes"
            :key="size"
            class="flex items-center gap-3 text-body text-label"
          >
            <span class="w-16 text-caption text-label-tertiary">{{ size }}</span>
            <span class="flex w-40 items-center gap-2">
              <Icon :as="Settings" :size="size" />
              <Icon :as="Bell" :size="size" />
              <Icon :as="ListChecks" :size="size" />
              <Icon :as="Trash2" :size="size" />
              <Icon :as="Plus" :size="size" />
            </span>
            <span class="flex items-center gap-1.5">
              <Icon :as="Settings" :size="size" />
              <span>设置 · 与 13px 中文同排</span>
            </span>
          </div>
        </div>
      </BaselineSection>

      <BaselineSection title="图标描边" hint="lucide 按 24px 网格设计，缩到 16px 时描边会等比变细">
        <div class="flex items-end gap-5">
          <div v-for="w in iconStrokes" :key="w" class="text-center">
            <Settings :size="16" :stroke-width="w" class="text-label" />
            <p class="mt-1 text-caption text-label-tertiary tnum">{{ w }}</p>
          </div>
          <div class="text-center">
            <Icon :as="Settings" />
            <p class="mt-1 text-caption text-accent tnum">1.4px</p>
          </div>
          <p class="ml-2 text-caption text-label-tertiary">
            16px 下的有效描边 = 描边值 × 16 / 24。最后一档是 Icon.vue 的取值：
            有效描边恒为 1.4px，不随尺寸变。
          </p>
        </div>
      </BaselineSection>

      <BaselineSection
        title="字重与数字"
        hint="中文的 600 等于 PingFang Semibold，比 SF 的 600 明显更重；数字列必须等宽"
      >
        <div class="space-y-2 text-body text-label">
          <p
            v-for="weight in fontWeights"
            :key="weight.value"
            :style="{ fontWeight: weight.value }"
            class="flex items-baseline gap-3"
          >
            <span class="w-24 shrink-0 text-caption font-normal text-label-tertiary">
              {{ weight.value }} · {{ weight.name }}
            </span>
            <span>修正登录页空指针 L3 待批准 Regent</span>
          </p>
        </div>
        <div class="mt-4 flex items-baseline gap-6">
          <div>
            <p class="text-caption text-label-tertiary">比例数字（默认）</p>
            <p class="text-body text-label">1024 1027 1023 1028 1025</p>
          </div>
          <div>
            <p class="text-caption text-label-tertiary">等宽数字 <code>.tnum</code></p>
            <p class="tnum text-body text-label">1024 1027 1023 1028 1025</p>
          </div>
        </div>
      </BaselineSection>

      <BaselineSection title="字号" hint="13px 是第一道分水岭">
        <div class="space-y-1.5">
          <p class="text-body text-label">13px 正文与控件（基准）— 探针 AI 判错的代价是白干一轮</p>
          <p class="text-[14px] text-label-secondary">14px — Web 通用默认，已经偏大</p>
          <p class="text-[16px] text-label-secondary">16px — 一眼网页感</p>
          <p class="text-caption text-label-tertiary">11px 辅助文字与分组标题</p>
          <p class="text-metric font-bold text-label tnum">1 024</p>
        </div>
      </BaselineSection>

      <BaselineSection title="控件尺寸" hint="24px 是第二道分水岭">
        <div class="flex items-end gap-3">
          <div class="text-center">
            <Button size="sm">sm</Button>
            <p class="mt-1 text-caption text-label-tertiary">20px</p>
          </div>
          <div class="text-center">
            <Button size="regular">regular</Button>
            <p class="mt-1 text-caption text-label-tertiary">24px</p>
          </div>
          <div class="text-center">
            <Button size="lg">lg</Button>
            <p class="mt-1 text-caption text-label-tertiary">28px</p>
          </div>
          <div class="text-center">
            <button
              type="button"
              class="h-8 rounded-md bg-neutral/25 px-3 text-body text-label-secondary"
            >
              32px
            </button>
            <p class="mt-1 text-caption text-label-tertiary">Element/antd 默认</p>
          </div>
        </div>
      </BaselineSection>

      <BaselineSection title="按钮">
        <div class="space-y-3">
          <div class="flex flex-wrap items-center gap-2">
            <Button>默认</Button>
            <Button variant="primary">主要</Button>
            <Button variant="danger">危险</Button>
            <Button variant="ghost">幽灵</Button>
            <Button disabled>禁用</Button>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <Button variant="primary" size="sm"><Icon :as="Plus" size="inline" />新建需求</Button>
            <Button variant="primary" size="lg"><Icon :as="Download" size="inline" />导出</Button>
            <Tooltip content="删除">
              <IconButton label="删除"><Icon :as="Trash2" size="inline" /></IconButton>
            </Tooltip>
            <IconButton label="更多" variant="default">
              <Icon :as="MoreHorizontal" size="inline" />
            </IconButton>
          </div>
          <div class="flex items-center gap-2 rounded-control bg-window px-2 py-1.5">
            <span class="mr-1 text-caption text-label-secondary">真实上下文</span>
            <Button size="sm">取消</Button>
            <Button size="sm" variant="primary">批准</Button>
            <span class="text-caption text-label-tertiary">
              — 一次只允许一个蓝色按钮，主次要分明
            </span>
          </div>
        </div>
      </BaselineSection>

      <BaselineSection title="表单控件">
        <div class="grid grid-cols-2 gap-x-8 gap-y-4 [&>label]:max-w-xs [&>div]:max-w-xs">
          <label class="flex flex-col gap-1">
            <span class="text-caption text-label-secondary">需求标题</span>
            <Input v-model="text" placeholder="描述这次要让 Regent 做什么" />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-caption text-label-secondary">空占位</span>
            <Input v-model="emptyText" placeholder="占位文字是 25% 的标签色" />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-caption text-label-secondary">校验失败</span>
            <Input v-model="invalidText" invalid />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-caption text-label-secondary">禁用</span>
            <Input model-value="不可编辑" disabled />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-caption text-label-secondary">项目</span>
            <Select v-model="project" :items="projects" />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-caption text-label-secondary">决策等级</span>
            <Select v-model="level" :items="levelOptions" size="sm" />
          </label>
          <div class="flex flex-col gap-2">
            <span class="text-caption text-label-secondary">选择类</span>
            <Checkbox v-model="checked" label="自动创建分支" />
            <Checkbox model-value="indeterminate" label="部分选中" />
            <RadioGroup v-model="pickOne" :items="[
              { value: 'a', label: '就高不就低' },
              { value: 'b', label: '按 AI 自评' },
            ]" />
          </div>
          <div class="flex flex-col gap-2">
            <span class="text-caption text-label-secondary">开关</span>
            <Switch v-model="enabled" label="任务完成后邮件通知" />
            <Switch v-model="autoRun" label="无人值守自动派发" />
            <Switch :model-value="false" disabled label="禁用态" />
          </div>
        </div>
      </BaselineSection>

      <BaselineSection title="分段控件" hint="macOS 最标志性的控件">
        <div class="flex flex-wrap items-center gap-4">
          <SegmentedControl v-model="filter" :items="filterOptions" />
          <SegmentedControl
            v-model="filter"
            :items="filterOptions.slice(0, 3)"
            size="sm"
          />
        </div>
      </BaselineSection>

      <BaselineSection
        title="表格"
        hint="28px 行高、无斑马纹；表头吸顶、数字右对齐，选中态必须压住行内颜色"
      >
        <div class="scroll-area overflow-x-auto">
          <Table
            class="min-w-[760px]"
            :columns="tableColumns"
            :rows="tableRows"
            :selected-id="selectedRow"
            @select="selectedRow = $event.id as number"
          >
            <template #header="{ column }">
              <Checkbox
                v-if="column.key === 'selection'"
                :model-value="tableSelectionState"
                aria-label="选择全部任务"
                @update:model-value="toggleAllTableRows($event === true)"
              />
              <span v-else>{{ column.label }}</span>
            </template>

            <template #cell="{ row, column }">
              <div
                v-if="column.key === 'selection'"
                class="flex items-center"
                @click.stop
              >
                <Checkbox
                  :model-value="checkedTableRows.includes(row.id)"
                  :aria-label="`选择任务 #${row.id}`"
                  @update:model-value="setTableRowChecked(row.id, $event === true)"
                />
              </div>
              <span
                v-else-if="column.key === 'id'"
                class="tnum text-label-tertiary group-data-[selected=true]:text-white"
              >
                {{ row.id }}
              </span>
              <span
                v-else-if="column.key === 'requirement'"
                class="font-emphasis group-data-[selected=true]:text-white"
              >
                {{ row.requirement }}
              </span>
              <DecisionLevelBadge v-else-if="column.key === 'level'" :level="row.level" />
              <TaskStatusBadge v-else-if="column.key === 'status'" :status="row.status" />
              <span
                v-else-if="column.key === 'duration'"
                class="tnum text-label-secondary group-data-[selected=true]:text-white"
              >
                {{ row.duration }}
              </span>
              <span
                v-else-if="column.key === 'tokens'"
                class="tnum text-label-secondary group-data-[selected=true]:text-white"
              >
                {{ row.tokens.toLocaleString('en-US') }}
              </span>
              <div
                v-else-if="column.key === 'actions'"
                class="flex justify-end"
                @click.stop
              >
                <Tooltip content="更多操作">
                  <IconButton label="更多操作" size="sm" variant="default">
                    <Icon :as="MoreHorizontal" size="inline" />
                  </IconButton>
                </Tooltip>
              </div>
            </template>
          </Table>
        </div>

        <p class="mt-4 text-caption text-label-tertiary">
          行点击切换选中态，复选框只演示键盘可达的真实控件。表头吸顶由调用方的滚动容器负责；
          长文本单行截断，完整内容应放在右侧检查器或详情弹框中。
        </p>

        <div class="mt-6 border-t border-separator pt-5">
          <div class="mb-3 flex items-baseline gap-2">
            <h3 class="text-body font-emphasis text-label">空表</h3>
            <p class="text-caption text-label-tertiary">表头保留，空态只出现一次</p>
          </div>
          <Table :columns="tableColumns" :rows="[]" />
        </div>
      </BaselineSection>

      <BaselineSection
        title="弹层与材质"
        hint="点一下才出现。静态能平铺的只有对话框，见下一节"
      >
        <div class="space-y-3">
          <div class="flex flex-wrap items-center gap-2">
            <Popover>
              <template #trigger>
                <Button>弹出层</Button>
              </template>
              <p class="text-body text-label">这是一个 Popover</p>
              <p class="mt-1 text-caption text-label-secondary">
                材质背景叠在内容之上，滚动列表时不要用它。
              </p>
            </Popover>

            <DropdownMenu>
              <template #trigger>
                <Button>下拉菜单</Button>
              </template>
              <DropdownMenuItem>批准并续跑</DropdownMenuItem>
              <DropdownMenuItem>改为 L3 强阻塞</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem disabled>驳回（理由可选）</DropdownMenuItem>
            </DropdownMenu>

            <Dialog
              v-model:open="dialogOpen"
              layout="form"
              title="驳回并挂起"
              description="任务会终止；理由可选，填写后会写入决策留痕。"
            >
              <template #trigger>
                <Button variant="primary">审批对话框（表单）</Button>
              </template>
              <Textarea v-model="rejectReason" :rows="3" placeholder="例如：改动会影响线上数据，需人工确认" />
              <template #footer>
                <Button @click="dialogOpen = false">取消</Button>
                <Button
                  variant="primary"
                  @click="dialogOpen = false"
                >
                  确认驳回
                </Button>
              </template>
            </Dialog>

            <Tooltip content="提示层也是材质">
              <Button variant="ghost">悬停看提示</Button>
            </Tooltip>
          </div>

          <div class="relative h-24 overflow-hidden rounded-panel bg-gradient-to-r from-accent/60 via-warning/50 to-success/50">
            <div class="absolute inset-x-0 top-0 flex h-toolbar-compact items-center bg-material px-3 backdrop-blur-[30px] backdrop-saturate-150">
              <span class="text-body text-label">材质层叠在彩色之上才会「活」</span>
            </div>
          </div>
        </div>
      </BaselineSection>

      <BaselineSection
        title="分页"
        hint="表格底部；翻页是一个两段式步进控件，不是两个独立按钮"
      >
        <div class="overflow-hidden rounded-panel border border-separator bg-window">
          <Pagination v-model:page="demoPage" v-model:page-size="demoPageSize" :total="137" />
        </div>
        <div class="mt-3 overflow-hidden rounded-panel border border-separator bg-window">
          <Pagination :page="1" :page-size="20" :total="0" />
        </div>

        <p class="mt-4 text-caption text-label-tertiary">
          左边只放读数（1–20 / 共 137 条），用 25% 的标签色 —— 它是参考信息，不是给人点的。
          右边是「每页多少」和翻页：两段合在一个圆角容器里、中间一条发丝线，这是
          NSSegmentedControl 的样子。拆成两个独立圆角按钮，一是显得像两个功能，
          二是和工具栏里的图标按钮撞脸。空态写「没有可显示的记录」，不写「1–0 / 共 0 条」。
          整条高 32px（`h-footer`），比工具栏矮一档。
        </p>
      </BaselineSection>

      <BaselineSection
        title="对话框"
        hint="静态平铺，用的是和弹出一模一样的 DialogPanel，所以看到的就是真实样子"
      >
        <!-- 样板铺灰底：对话框是半透明材质，落在白底上读不出「浮起来」 -->
        <div class="flex flex-wrap items-start gap-4 rounded-panel bg-window p-5">
          <!-- 普通确认：窄、无图标、文字左对齐，按钮右下角「取消在左、默认动作在右」 -->
          <DialogPanel layout="alert">
            <template #header>
              <h3 :class="dialogTitleClass">批准这条决策？</h3>
              <p :class="dialogDescriptionClass">批准后将从断点继续执行，上下文不丢。</p>
            </template>
            <p class="text-caption text-label-secondary">
              #1026 · L3 强阻塞 · 命中规则「deploy/**」
            </p>
            <template #footer>
              <Button>取消</Button>
              <Button variant="primary">批准</Button>
            </template>
          </DialogPanel>

          <!-- 破坏性操作：图标给一个，默认动作是红的 —— 全站只有这一处可以用红色实心 -->
          <DialogPanel layout="alert" :icon="AlertTriangle" icon-tone="danger">
            <template #header>
              <h3 :class="dialogTitleClass">删除任务 #1023？</h3>
              <p :class="dialogDescriptionClass">运行记录与分支引用会一并删除，无法撤销。</p>
            </template>
            <template #footer>
              <Button>取消</Button>
              <Button variant="danger">删除</Button>
            </template>
          </DialogPanel>

          <!-- form：驳回可补充理由。L2/L3 的审批走这一款 -->
          <DialogPanel layout="form">
            <template #header>
              <h3 :class="dialogTitleClass">驳回并挂起</h3>
              <p :class="dialogDescriptionClass">任务会终止；理由可选，填写后会写入决策留痕。</p>
            </template>
            <Textarea
              v-model="rejectReason"
              :rows="3"
              placeholder="这个改动会影响线上数据，需人工确认"
            />
            <template #footer>
              <Button>取消</Button>
              <Button variant="primary">确认驳回</Button>
            </template>
          </DialogPanel>

          <!-- form + large：要写长文、还要贴图的表单。新建任务就是这一款 -->
          <DialogPanel layout="form" size="large">
            <template #header>
              <h3 :class="dialogTitleClass">新建任务</h3>
              <p :class="dialogDescriptionClass">任务先进待执行队列，派发之后 codex 才会跑。</p>
            </template>
            <Textarea :rows="6" placeholder="交给 codex 的完整指令：要改哪里、验收标准是什么" />
            <template #footer>
              <Button>取消</Button>
              <Button variant="primary">创建任务</Button>
            </template>
          </DialogPanel>
        </div>

        <p class="mt-4 text-caption text-label-tertiary">
          宽度四档：alert 340px（问「要不要做」），form 384px（短表单，如「补充驳回理由」），
          form + large 640px（长表单：要写整段指令、要贴图、还要选项目），log 640px（放日志）。
          标题与说明一律左对齐、图标在左上角，这是 NSAlert 的样子，不是 iOS 的居中弹窗。
          弹层、下拉菜单、提示层靠 reka-ui 的定位渲染，没法静态平铺，只能点开看。
        </p>
      </BaselineSection>

      <BaselineSection
        title="提示（Toast）"
        hint="点一下看真实效果 —— 它浮在最上层，没法静态平铺"
      >
        <div class="flex flex-wrap items-center gap-2">
          <Button @click="toast.success('已批准 #1026', '执行器正从断点续跑')">成功</Button>
          <Button @click="toast.show('已驳回 #1027', { description: '决策已留痕' })">
            默认
          </Button>
          <Button @click="toast.warning('日志流尚未接入', '实时推送就绪后这里会打开完整日志')">
            警告
          </Button>
          <Button @click="toast.failure('派发失败', 'codex 可执行文件不在 PATH 里')">失败</Button>
        </div>

        <p class="mt-4 text-caption text-label-tertiary">
          位置固定在右上、工具栏之下（macOS 通知横幅的位置），全站只挂一个宿主。
          6px 的小色点区分语义，不做彩色胶囊、不整块上底色。标题说「发生了什么」，
          说明说「接下来会怎样」——只有标题的提示等于没说。
        </p>
      </BaselineSection>

      <BaselineSection title="状态与徽章">
        <div class="space-y-3">
          <div class="flex flex-wrap items-center gap-2">
            <Badge tone="neutral">默认</Badge>
            <Badge tone="accent">执行中</Badge>
            <Badge tone="success">已完成</Badge>
            <Badge tone="warning">待批准</Badge>
            <Badge tone="danger">失败</Badge>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <DecisionLevelBadge level="L0" />
            <DecisionLevelBadge level="L1" />
            <DecisionLevelBadge level="L2" />
            <DecisionLevelBadge level="L3" />
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <TaskStatusBadge status="PENDING" />
            <TaskStatusBadge status="QUEUED" />
            <TaskStatusBadge status="RUNNING" />
            <TaskStatusBadge status="BLOCKED" />
            <TaskStatusBadge status="DONE" />
            <TaskStatusBadge status="FAILED" />
            <TaskStatusBadge status="CANCELLED" />
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <BugSeverityBadge severity="BLOCKER" />
            <BugSeverityBadge severity="MAJOR" />
            <BugSeverityBadge severity="MINOR" />
            <BugSeverityBadge severity="TRIVIAL" />
            <span class="text-caption text-label-tertiary">·</span>
            <BugStatusBadge status="OPEN" />
            <BugStatusBadge status="FIXING" />
            <BugStatusBadge status="FIXED" />
            <BugStatusBadge status="CLOSED" />
            <BugStatusBadge status="WONTFIX" />
          </div>

          <!--
            选中态：底色是实心强调色，色点必须整个转白。加白描边是没用的 ——
            1px 的环落在蓝底上根本看不见，蓝点会直接消失。
          -->
          <div class="flex flex-wrap items-center gap-3">
            <span
              class="group inline-flex items-center gap-3 rounded-control bg-accent px-3 py-1.5 text-body text-white"
              data-selected="true"
            >
              <StatusDot dot="bg-accent">修复中</StatusDot>
              <StatusDot dot="bg-danger">阻塞</StatusDot>
            </span>
            <span class="text-caption text-label-secondary">整行被选中时的色点</span>
          </div>
          <div class="flex items-center gap-3">
            <Avatar name="Panyong" />
            <Avatar name="Regent" size="lg" />
            <div class="min-w-0 flex-1">
              <Progress :value="62" />
            </div>
            <span class="tnum text-caption text-label-secondary">62%</span>
          </div>
          <div class="flex items-center gap-2">
            <Skeleton class="h-4 w-32" />
            <Skeleton class="h-4 w-20" />
          </div>
          <div class="rounded-panel bg-window">
            <EmptyState title="还没有决策" description="任务遇到需要拍板的地方才会出现在这里" />
          </div>
        </div>
      </BaselineSection>

      <BaselineSection title="颜色与令牌" hint="强调色只用系统色，不自造">
        <div class="flex flex-wrap items-center gap-x-5 gap-y-2">
          <div v-for="token in colorTokens" :key="token.label" class="flex items-center gap-1.5">
            <span class="size-3 rounded-full" :class="token.class" />
            <span class="text-caption text-label-secondary">{{ token.label }}</span>
          </div>
        </div>
        <div class="mt-4 flex items-center gap-3">
          <div class="h-8 flex-1 rounded-control bg-window shadow-control" />
          <div class="h-8 flex-1 rounded-panel bg-content shadow-panel" />
          <div class="h-8 flex-1 rounded-window bg-material shadow-window" />
        </div>
        <div class="mt-3 flex items-center gap-2">
          <div class="h-6 w-16 rounded-control bg-neutral/10 dark:bg-neutral/40" />
          <p class="text-caption text-label-secondary">
            dark: 变体（同一元素在深浅下取不同值，用于必须显式分叉的少数场合）
          </p>
        </div>
        <div class="mt-3 space-y-1">
          <p class="text-body text-label">label · 85%</p>
          <p class="text-body text-label-secondary">label-secondary · 50%</p>
          <p class="text-body text-label-tertiary">label-tertiary · 25%</p>
        </div>
      </BaselineSection>

      <BaselineSection title="深浅并排" hint="同一个 .dark 容器内，令牌自动切换">
        <div class="grid grid-cols-2 gap-3">
          <div class="rounded-panel bg-window p-3">
            <p class="mb-2 text-caption text-label-secondary">浅色</p>
            <div class="space-y-2">
              <Input v-model="text" />
              <div class="flex items-center gap-2">
                <Button variant="primary" size="sm">批准</Button>
                <Switch v-model="enabled" />
                <Badge tone="accent">执行中</Badge>
              </div>
            </div>
          </div>
          <div class="dark rounded-panel bg-window p-3">
            <p class="mb-2 text-caption text-label-secondary">深色</p>
            <div class="space-y-2">
              <Input v-model="text" />
              <div class="flex items-center gap-2">
                <Button variant="primary" size="sm">批准</Button>
                <Switch v-model="enabled" />
                <Badge tone="accent">执行中</Badge>
              </div>
            </div>
          </div>
        </div>
      </BaselineSection>
    </div>
   </div>
  </div>
</template>
