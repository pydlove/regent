<script setup lang="ts">
import { computed } from 'vue'

import Icon from '@/components/ui/icon/Icon.vue'

import {
  dialogIconVariants,
  dialogPanelVariants,
  dialogTrafficCloseVariants,
  dialogTrafficLightVariants,
  type DialogIcon,
  type DialogIconProps,
  type DialogVariantProps,
} from './dialog.variants'

const props = withDefaults(
  defineProps<{
    layout?: DialogVariantProps['layout']
    /** 弹框宽度：large 用于长表单，wide 只给需要横排查看的日志类弹框 */
    size?: DialogVariantProps['size']
    /** compact 把最大高度收敛到视口的 80% */
    maxHeight?: DialogVariantProps['maxHeight']
    /** fixed 用于内容量变化时仍保持稳定尺寸的日志类弹框 */
    height?: DialogVariantProps['height']
    /** 占满整个视口；用于日志、事件流等需要大面积查看的内容 */
    fullscreen?: boolean
    /** 左上角图标。警告类给一个，普通确认可以不给 —— 原生也不是每个警告框都带图标 */
    icon?: DialogIcon
    iconTone?: DialogIconProps['tone']
  }>(),
  {
    layout: 'alert',
    size: 'regular',
    maxHeight: 'default',
    height: 'auto',
    fullscreen: false,
    icon: undefined,
    iconTone: 'default',
  },
)

/* 红点要真的关掉弹框，但面板只管外观（见下方模板注释），所以把动作抛给 Dialog.vue */
const emit = defineEmits<{ close: [] }>()

const panelClasses = computed(() =>
  dialogPanelVariants({
    layout: props.layout,
    size: props.size,
    maxHeight: props.maxHeight,
    height: props.height,
    fullscreen: props.fullscreen,
  }),
)
const panelStyle = computed(() =>
  props.fullscreen
    ? {
        width: '100vw',
        height: '100dvh',
        maxWidth: 'none',
        maxHeight: 'none',
        borderRadius: '0',
      }
    : undefined,
)
const iconClasses = computed(() => dialogIconVariants({ tone: props.iconTone }))
</script>

<template>
  <!-- 只负责外观，不碰定位与无障碍：定位在 Dialog.vue 的 DialogContent 上，
       aria 由 reka-ui 的 DialogTitle / DialogDescription 提供。
       正因为解耦，基准页才能把同一块面板静态平铺出来。 -->
  <div :class="panelClasses" :style="panelStyle">
    <div class="shrink-0">
      <!-- 标题栏：三颗窗口按钮在最左，间距 8px（AppKit 的实测值）。
           红点是唯一能点的一颗，动作往外抛 —— 面板本身不碰业务状态 -->
      <div v-if="!props.fullscreen" class="group/traffic mb-3 flex items-center gap-2">
        <button
          type="button"
          tabindex="-1"
          aria-label="关闭"
          :class="[dialogTrafficCloseVariants(), 'transition-transform active:scale-90']"
          @click="emit('close')"
        >
          <!-- 悬停整组才露出 ×，与 macOS 一致。12 单位视图里 3.5→8.5 是 5px，描边 1.1px -->
          <svg
            viewBox="0 0 12 12"
            class="size-3 text-black/40 opacity-0 transition-opacity duration-100 ease-apple group-hover/traffic:opacity-100"
            aria-hidden="true"
          >
            <path
              d="M3.5 3.5 8.5 8.5M8.5 3.5 3.5 8.5"
              fill="none"
              stroke="currentColor"
              stroke-width="1.1"
              stroke-linecap="round"
            />
          </svg>
        </button>
        <span :class="dialogTrafficLightVariants({ tone: 'minimize' })" aria-hidden="true" />
        <span :class="dialogTrafficLightVariants({ tone: 'zoom' })" aria-hidden="true" />
      </div>

      <div class="flex items-start gap-3">
        <Icon
          v-if="icon"
          :as="icon"
          size="large"
          :class="['mt-0.5 shrink-0', iconClasses]"
        />
        <div class="min-w-0 flex-1">
          <slot name="header" />
        </div>
      </div>
    </div>

    <!-- 正文是唯一可滚动区域，标题与操作按钮始终留在视口内。 -->
    <div class="scroll-area mt-3 flex min-h-0 flex-1 flex-col overflow-y-auto overscroll-contain text-body text-label">
      <slot />
    </div>
    <!-- 按钮间距 12px：macOS 的警告框里两个按钮之间就是这个距离，8px 会显得挤成一团 -->
    <div class="mt-4 flex shrink-0 justify-end gap-3">
      <slot name="footer" />
    </div>
  </div>
</template>
