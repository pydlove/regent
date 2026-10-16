<script setup lang="ts">
import { computed } from 'vue'

import { badgeVariants, type BadgeVariantProps } from './badge.variants'

const props = withDefaults(
  defineProps<{
    tone?: BadgeVariantProps['tone']
    /** 调用点的样式覆盖要经 tv 走一遍 twMerge，不能让 class 从 attribute 透传：
        那样两套 bg-* 会同时挂在元素上，谁赢取决于 Tailwind 的产物顺序，不由调用点决定。 */
    class?: string
  }>(),
  { tone: 'neutral', class: '' },
)

const classes = computed(() => badgeVariants({ tone: props.tone, class: props.class }))
</script>

<template>
  <span :class="classes"><slot /></span>
</template>
