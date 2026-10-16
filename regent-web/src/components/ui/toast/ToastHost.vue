<script setup lang="ts">
import { X } from '@lucide/vue'

import Icon from '@/components/ui/icon/Icon.vue'
import { useToast } from '@/composables/useToast'
import { toastDotVariants, toastVariants } from './toast.variants'

/*
 * 全站唯一的提示宿主，挂一次即可（App.vue）。
 *
 * 位置选了右上、工具栏之下：这是 macOS 通知横幅的位置习惯。放在正上方会和窗口标题打架，
 * 放在右下会和右侧检查器抢视线。
 *
 * 用 TransitionGroup 而不是给每条自己写动画：入场与出场的时序由 Vue 管，
 * 连续触发多条时不会被后一条打断前一条的收起动作。
 */
const { items, dismiss } = useToast()
</script>

<template>
  <Teleport to="body">
    <TransitionGroup
      tag="div"
      class="pointer-events-none fixed right-4 top-14 z-[60] flex flex-col items-end gap-2"
      enter-from-class="opacity-0 -translate-y-2"
      enter-active-class="transition duration-200 ease-apple"
      leave-to-class="opacity-0"
      leave-active-class="transition duration-150 ease-apple"
    >
      <div v-for="item in items" :key="item.id" :class="toastVariants()">
        <span :class="toastDotVariants({ tone: item.tone })" />
        <div class="min-w-0 flex-1">
          <p class="text-body text-label">{{ item.title }}</p>
          <p v-if="item.description" class="mt-0.5 text-caption text-label-secondary">
            {{ item.description }}
          </p>
        </div>
        <button
          type="button"
          class="-mr-1 -mt-1 shrink-0 rounded-control p-1 text-label-tertiary transition-colors duration-[120ms] ease-apple hover:bg-hover hover:text-label"
          aria-label="关闭"
          @click="dismiss(item.id)"
        >
          <Icon :as="X" size="inline" />
        </button>
      </div>
    </TransitionGroup>
  </Teleport>
</template>
