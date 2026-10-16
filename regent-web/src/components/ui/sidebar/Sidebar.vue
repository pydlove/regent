<script setup lang="ts">
import type { Component } from 'vue'
import { computed } from 'vue'

import Badge from '@/components/ui/badge/Badge.vue'
import Icon from '@/components/ui/icon/Icon.vue'

type SidebarItem = { label: string; to: string; icon?: Component; badge?: string }

const props = withDefaults(
  defineProps<{
    items: SidebarItem[]
    /**
     * 底部展示类入口（样式基准页这类）。用 mt-auto 压到侧边栏最底部、与业务项隔一条
     * 发丝线：它不参与业务流程，位置就不该和业务项混在一起。
     */
    footerItems?: SidebarItem[]
    collapsed?: boolean
  }>(),
  { footerItems: () => [], collapsed: false },
)

const entries = computed(() => [
  ...props.items.map((item) => ({ ...item, spacerBefore: false })),
  ...props.footerItems.map((item, index) => ({ ...item, spacerBefore: index === 0 })),
])
</script>

<template>
  <!-- 折叠时收到 0，不退化成图标竖条 —— 那是 Windows/Web 的习惯，见 FRONTEND.md 第七节 -->
  <aside
    :class="[
      'shrink-0 overflow-hidden border-r border-separator bg-material backdrop-blur-[30px] backdrop-saturate-150 transition-[width] duration-200 ease-apple',
      collapsed ? 'w-0 border-r-0' : 'w-sidebar',
    ]"
  >
    <nav class="scroll-area flex h-full flex-col gap-px overflow-y-auto p-2">
      <template v-for="item in entries" :key="item.to">
        <!-- 先撑开剩余空间把展示类入口压到底，再用一条发丝线与业务项分开 -->
        <div v-if="item.spacerBefore" class="mt-auto pt-3">
          <div class="h-px bg-separator-soft" />
        </div>
        <RouterLink
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
            v-if="item.badge"
            class="ml-auto text-label-secondary group-aria-[current=page]:bg-white/25 group-aria-[current=page]:text-white"
          >
            {{ item.badge }}
          </Badge>
        </RouterLink>
      </template>

      <slot />
    </nav>
  </aside>
</template>
