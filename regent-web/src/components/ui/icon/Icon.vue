<script setup lang="ts">
import type { Component } from 'vue'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    as: Component
    /**
     * 前三档是「控件里的图标」，跟控件尺寸走；
     * xlarge 是「插图」，只用于空态、对话框这类有大留白的位置，不要出现在行内。
     */
    size?: 'inline' | 'regular' | 'large' | 'xlarge'
  }>(),
  { size: 'regular' },
)

const px = computed(() => ({ inline: 14, regular: 16, large: 20, xlarge: 32 })[props.size])

/*
 * 有效描边固定 1.4px，不随尺寸变。
 *
 * lucide 的 viewBox 是 24，渲染到 px 时描边等比缩放：stroke-width=1.5 落到 16px 只剩 1px，
 * 比 13px 中文的竖画还细 —— 图标于是发虚，和旁边的字不在一个重量级。SF Symbols 在同一
 * 字号下描边是恒定的，所以这里也反算回去：strokeWidth = 目标描边 × 24 / 渲染尺寸。
 * 直接抄 SF Symbols 的 1.4px（16pt Regular 档的实测值）。
 */
const TARGET_STROKE = 1.4
const stroke = computed(() => Math.round(((TARGET_STROKE * 24) / px.value) * 100) / 100)
</script>

<template>
  <component :is="as" :size="px" :stroke-width="stroke" class="shrink-0" />
</template>
