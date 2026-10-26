<script setup lang="ts">
import {
  DialogContent,
  DialogDescription,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle,
  DialogTrigger,
} from 'reka-ui'

import { useOverlayLayer } from '@/composables/useOverlayLayer'

import DialogPanel from './DialogPanel.vue'
import {
  dialogDescriptionClass,
  dialogTitleClass,
  type DialogIcon,
  type DialogIconProps,
  type DialogVariantProps,
} from './dialog.variants'

const props = withDefaults(
  defineProps<{
    title: string
    description?: string
    layout?: DialogVariantProps['layout']
    /** 弹框宽度：large 用于一般长表单，wide 用于复杂录入和日志类弹框 */
    size?: DialogVariantProps['size']
    /** compact 把最大高度收敛到视口的 80% */
    maxHeight?: DialogVariantProps['maxHeight']
    /** fixed 使用稳定的日志弹框高度，内容只在内部区域滚动 */
    height?: DialogVariantProps['height']
    fullscreen?: boolean
    icon?: DialogIcon
    iconTone?: DialogIconProps['tone']
    /** 表单类弹框可关闭“点击遮罩关闭”，避免误触丢失未保存内容。 */
    closeOnOutsideClick?: boolean
  }>(),
  {
    description: '',
    layout: 'alert',
    size: 'regular',
    maxHeight: 'default',
    height: 'auto',
    fullscreen: false,
    icon: undefined,
    iconTone: 'default',
    closeOnOutsideClick: true,
  },
)

const open = defineModel<boolean>('open', { default: false })

/*
 * 层级按「打开顺序」发号，不写死 z-50：两个弹框同时开着时（比如新建任务里直接注册项目），
 * 谁在上面由 dialog 组件说了算，不由它在 body 里的插入位置说了算。见 useOverlayLayer。
 */
const { zIndex } = useOverlayLayer(open)

function handlePointerDownOutside(event: Event) {
  if (!props.closeOnOutsideClick) event.preventDefault()
}
</script>

<template>
  <DialogRoot v-model:open="open">
    <DialogTrigger as-child>
      <slot name="trigger" />
    </DialogTrigger>
    <DialogPortal>
      <!-- 遮罩只做轻微压暗：macOS 的模态是「父窗口退后」，不是「盖一层黑布」 -->
      <DialogOverlay class="fixed inset-0 animate-overlay-in bg-black/25" :style="{ zIndex }" />
      <!-- 定位留给 DialogContent，动效留给 DialogPanel —— 两者抢同一个 transform 会打架 -->
      <DialogContent
        class="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2"
        :style="{ zIndex }"
        @pointer-down-outside="handlePointerDownOutside"
      >
        <DialogPanel
          :layout="layout"
          :size="size"
          :max-height="maxHeight"
          :height="height"
          :fullscreen="fullscreen"
          :icon="icon"
          :icon-tone="iconTone"
          @close="open = false"
        >
          <template #header>
            <div class="flex items-center gap-3">
              <slot name="header-leading" />
              <div class="min-w-0 flex-1">
                <DialogTitle :class="dialogTitleClass">{{ title }}</DialogTitle>
                <DialogDescription v-if="description" :class="dialogDescriptionClass">
                  {{ description }}
                </DialogDescription>
              </div>
              <slot name="header-action" />
            </div>
          </template>
          <slot />
          <template #footer>
            <slot name="footer" />
          </template>
        </DialogPanel>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
