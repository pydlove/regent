<script setup lang="ts" generic="T extends Record<string, unknown>">
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'

import type { TableColumn } from './table.types'

const props = withDefaults(
  defineProps<{
    columns: TableColumn[]
    rows: T[]
    rowKey?: string
    selectedId?: string | number | null
    /**
     * 空态兜底。调用方自己有更具体的空态（「没有待批决策」这类）时传 false，
     * 否则表格的「暂无数据」会和页面的空态**同时出现**，一个屏幕上两个空。
     */
    showEmpty?: boolean
  }>(),
  { rowKey: 'id', selectedId: null, showEmpty: true },
)

const emit = defineEmits<{ select: [row: T] }>()

function cellText(row: T, key: string): string {
  const value = row[key]
  return value === null || value === undefined ? '' : String(value)
}

function isSelected(row: T): boolean {
  return props.selectedId !== null && row[props.rowKey] === props.selectedId
}
</script>

<template>
  <!--
    规格见 FRONTEND.md 第八节：无外框、无斑马纹、选中是实心强调色。
    这里刻意**不自带滚动容器**：一旦根节点写了 overflow-auto，它就成了最近的滚动盒子，
    而它自己永远不滚 —— 表头的 position:sticky 会认这个盒子，结果就是表头根本不吸顶。
    滚动交给调用方的容器，sticky 才会认对祖先，滚动条样式也才落在真正滚的那个元素上。
  -->
  <div>
    <!--
      table-fixed 不是随手加的：行高按规格固定 28px（FRONTEND.md 第八节），而自动表格布局下
      单元格的宽度由内容决定 —— 一条 60 字的任务标题会把这行撑成三行，一屏的行高参差不齐。
      固定布局让列宽只认表头的 width 与剩余空间的分配，单元格里再用 truncate 收尾，
      行高就真的固定住了。代价：多列都没有 width 时它们平分剩余宽度，所以列宽要写全。
    -->
    <table class="w-full table-fixed border-separate border-spacing-0 text-body">
      <thead>
        <tr>
          <th
            v-for="column in columns"
            :key="column.key"
            :style="{ width: column.width }"
            :class="[
              'sticky top-0 z-10 h-6 border-b border-separator bg-content px-2 text-caption font-emphasis text-label-secondary',
              column.align === 'right' ? 'text-right' : 'text-left',
              column.numeric ? 'tnum' : '',
            ]"
          >
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in rows"
          :key="String(row[rowKey])"
          :class="[
            'group h-row cursor-default',
            isSelected(row) ? 'bg-accent text-white' : 'hover:bg-hover',
          ]"
          :data-selected="isSelected(row) ? 'true' : undefined"
          @click="emit('select', row)"
        >
          <td
            v-for="column in columns"
            :key="column.key"
            :class="[
              'border-b border-separator-soft px-2',
              column.align === 'right' ? 'text-right' : 'text-left',
              column.numeric || column.align === 'right' ? 'tnum' : '',
            ]"
          >
            <!-- 单元格内容一律截断成一行：完整内容在右侧详情/弹框里，列表不是读长文的地方 -->
            <div class="truncate">
              <slot name="cell" :row="row" :column="column" :value="row[column.key]">
                {{ cellText(row, column.key) }}
              </slot>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <EmptyState
      v-if="showEmpty && rows.length === 0"
      title="暂无数据"
      description="还没有可展示的记录"
    />
  </div>
</template>
