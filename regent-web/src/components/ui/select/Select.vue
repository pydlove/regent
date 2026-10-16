<script setup lang="ts">
import { Check, ChevronDown } from '@lucide/vue'
import {
  SelectContent,
  SelectIcon,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectPortal,
  SelectRoot,
  SelectTrigger,
  SelectValue,
  SelectViewport,
} from 'reka-ui'
import { computed, ref, useAttrs } from 'vue'

import { buttonVariants, type ButtonVariantProps } from '@/components/ui/button/button.variants'
import Icon from '@/components/ui/icon/Icon.vue'
import { tv } from '@/components/ui/tv'
import { useOverlayLayer } from '@/composables/useOverlayLayer'

/*
 * SelectRoot 是 renderless 的，槽里是「触发器 + Portal」两个节点，属于 fragment 根，
 * Vue 的自动 attrs 透传在这里会**静默失效** —— 外面写的 class="w-44" 直接消失，
 * 而且没有任何告警。所以显式关掉自动透传、手动绑到触发器上。
 * 这个坑是给分页条的「每页 20 条」定宽时才发现的，工具栏里的项目下拉一直在裸奔。
 */
defineOptions({ inheritAttrs: false })
const attrs = useAttrs()

const props = withDefaults(
  defineProps<{
    items: { value: string; label: string }[]
    placeholder?: string
    size?: ButtonVariantProps['size']
    disabled?: boolean
    block?: boolean
  }>(),
  { placeholder: '请选择', size: 'regular', disabled: false, block: false },
)

const model = defineModel<string | undefined>()

// 下拉面板也是浮层，层级同样按「打开顺序」发号：否则弹框里的下拉会被自己弹框的遮罩压住
const open = ref(false)
const { zIndex } = useOverlayLayer(open)

/*
 * 触发器 = 按钮外观 + 两端对齐（文字靠左、箭头贴右）。
 *
 * 必须用 tv 的 extend 合成一个 class 串，不能写成 `:class="[buttonClasses, 'justify-between']"`：
 * 数组元素之间没有 twMerge，Button 的 `justify-center` 与这里的 `justify-between` 会同时
 * 挂在元素上，谁生效取决于 Tailwind 产物里的先后顺序 —— 实测是 justify-center 赢，
 * 于是下拉框里的文字变成居中。macOS 的弹出式按钮一律文字靠左、箭头贴右，居中一眼就露馅。
 */
const triggerVariants = tv({ extend: buttonVariants, base: 'justify-between gap-2' })

const triggerClasses = computed(() =>
  triggerVariants({ variant: 'default', size: props.size, block: props.block }),
)
</script>

<template>
  <SelectRoot v-model="model" v-model:open="open" :disabled="disabled">
    <SelectTrigger
      v-bind="attrs"
      :class="triggerClasses"
      aria-label="选择"
    >
      <SelectValue :placeholder="placeholder" />
      <SelectIcon>
        <Icon :as="ChevronDown" class="text-label-secondary" />
      </SelectIcon>
    </SelectTrigger>

    <SelectPortal>
      <SelectContent
        position="popper"
        :side-offset="4"
        class="min-w-[var(--reka-select-trigger-width)] overflow-hidden rounded-panel bg-material p-1 shadow-popover backdrop-blur-[30px] backdrop-saturate-150"
        :style="{ zIndex }"
      >
        <SelectViewport>
          <SelectItem
            v-for="item in items"
            :key="item.value"
            :value="item.value"
            class="flex h-control cursor-default select-none items-center gap-2 rounded-control pl-5 pr-2 text-body text-label outline-none data-[highlighted]:bg-accent data-[highlighted]:text-white"
          >
            <SelectItemIndicator class="absolute left-1.5">
              <Icon :as="Check" size="inline" />
            </SelectItemIndicator>
            <SelectItemText>{{ item.label }}</SelectItemText>
          </SelectItem>
        </SelectViewport>
      </SelectContent>
    </SelectPortal>
  </SelectRoot>
</template>
