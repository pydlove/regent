<script setup lang="ts">
import type { Component } from 'vue'

import Badge from '@/components/ui/badge/Badge.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import { formatCountBadge } from '@/utils/countBadge'

type SidebarItem = {
  label: string
  to: string
  icon?: Component
  badge?: number
  badgeLabel?: string
}

type SidebarGroup = {
  label: string
  items: SidebarItem[]
}

const props = withDefaults(
  defineProps<{
    groups: SidebarGroup[]
    collapsed?: boolean
  }>(),
  { collapsed: false },
)
</script>

<template>
  <!-- 折叠时收到 0，不退化成图标竖条 —— 那是 Windows/Web 的习惯，见 FRONTEND.md 第七节 -->
  <!--
    手机端（<lg）改用抽屉式覆盖：侧边栏浮在正文上，不挤压 390px 的列表。
    桌面上仍是占位的折叠/展开两态，动画从宽度走；手机上从位移走，互不干扰。
  -->
  <aside
    :class="[
      'shrink-0 overflow-hidden border-r border-separator bg-material backdrop-blur-[30px] backdrop-saturate-150 transition-[width,transform] duration-200 ease-apple max-lg:fixed max-lg:inset-y-0 max-lg:left-0 max-lg:z-30 max-lg:w-sidebar max-lg:shadow-window',
      collapsed
        ? 'max-lg:-translate-x-full max-lg:border-r-0 lg:w-0 lg:border-r-0'
        : 'max-lg:translate-x-0 lg:w-sidebar',
    ]"
  >
    <nav class="scroll-area flex h-full flex-col gap-px overflow-y-auto p-2">
      <template v-for="(group, index) in props.groups" :key="group.label">
        <p
          class="px-2 text-[10px] font-emphasis leading-[14px] text-label-tertiary"
          :class="index === 0 ? 'pb-1 pt-1' : 'pb-1 pt-3'"
        >
          {{ group.label }}
        </p>
        <RouterLink
          v-for="item in group.items"
          :key="item.to"
          :to="item.to"
          class="group flex h-row items-center gap-2 rounded-[6px] px-2 text-body text-label transition-colors duration-[120ms] ease-apple hover:bg-hover aria-[current=page]:bg-accent aria-[current=page]:text-white"
        >
          <!-- 图标用 text-label 而不是 text-label-secondary：系统侧边栏的图标是实心符号，
               比文字重；这里只有描边，压到 50% 会明显发虚、看着比字小一圈。 -->
          <Icon
            v-if="item.icon"
            :as="item.icon"
            class="text-label group-aria-[current=page]:text-white"
          />
          <span class="truncate">{{ item.label }}</span>
          <Badge
            v-if="formatCountBadge(item.badge ?? 0)"
            class="tnum ml-auto min-w-5 shrink-0 justify-center whitespace-nowrap px-1.5 text-label-secondary group-aria-[current=page]:bg-white/25 group-aria-[current=page]:text-white"
            :title="item.badgeLabel"
            :aria-label="item.badgeLabel"
          >
            {{ formatCountBadge(item.badge ?? 0) }}
          </Badge>
        </RouterLink>
      </template>

      <slot />
    </nav>
  </aside>
</template>
