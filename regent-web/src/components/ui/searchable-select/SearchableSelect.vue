<script setup lang="ts">
import { Check, ChevronDown, Search } from '@lucide/vue'
import {
  ComboboxAnchor,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxItemIndicator,
  ComboboxPortal,
  ComboboxRoot,
  ComboboxTrigger,
  ComboboxViewport,
} from 'reka-ui'
import { computed, ref, useAttrs } from 'vue'

import { buttonVariants, type ButtonVariantProps } from '@/components/ui/button/button.variants'
import Icon from '@/components/ui/icon/Icon.vue'
import { tv } from '@/components/ui/tv'
import { useOverlayLayer } from '@/composables/useOverlayLayer'

defineOptions({ inheritAttrs: false })
const attrs = useAttrs()

const props = withDefaults(
  defineProps<{
    items: { value: string; label: string }[]
    placeholder?: string
    emptyText?: string
    ariaLabel?: string
    size?: ButtonVariantProps['size']
    disabled?: boolean
    block?: boolean
  }>(),
  {
    placeholder: '请选择',
    emptyText: '没有匹配项',
    ariaLabel: '搜索并选择',
    size: 'regular',
    disabled: false,
    block: false,
  },
)

const model = defineModel<string>({ default: '' })
const open = ref(false)
const { zIndex } = useOverlayLayer(open)

/*
 * Combobox 的输入框同时承担“显示当前值”和“输入搜索词”两个职责。
 * 复用按钮变体保留工具栏下拉的外观，再覆盖文字选择和激活缩放，避免输入框像按钮一样不可选文字。
 */
const inputVariants = tv({
  extend: buttonVariants,
  base: 'select-text text-left active:scale-100',
})

const inputClasses = computed(() =>
  inputVariants({
    variant: 'default',
    size: props.size,
    block: props.block,
    // extend 的父级尺寸类会排在 base 后；作为 class 传入才能最终覆盖 px-4。
    class: 'px-8',
  }),
)

const displayValue = (value: string) =>
  props.items.find((item) => item.value === value)?.label ?? ''

const selectInputText = (event: FocusEvent) => {
  const input = event.currentTarget as HTMLInputElement
  input.select()
}
</script>

<template>
  <ComboboxRoot
    v-model="model"
    v-model:open="open"
    :disabled="disabled"
    open-on-focus
    open-on-click
  >
    <ComboboxAnchor class="relative block">
      <ComboboxInput
        v-bind="attrs"
        :class="inputClasses"
        :display-value="displayValue"
        :placeholder="placeholder"
        :aria-label="ariaLabel"
        inputmode="search"
        autocomplete="off"
        @focus="selectInputText"
      />
      <Icon
        :as="Search"
        size="inline"
        class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-label-secondary"
      />
      <ComboboxTrigger
        as-child
        :disabled="disabled"
      >
        <button
          type="button"
          class="group absolute inset-y-0 right-0 flex w-7 items-center justify-center text-label-secondary disabled:opacity-40"
          aria-label="展开或收起项目选项"
        >
          <Icon
            :as="ChevronDown"
            class="transition-transform duration-[120ms] ease-apple group-data-[state=open]:rotate-180"
          />
        </button>
      </ComboboxTrigger>
    </ComboboxAnchor>

    <ComboboxPortal>
      <ComboboxContent
        position="popper"
        :side-offset="4"
        class="min-w-[var(--reka-combobox-trigger-width)] overflow-hidden rounded-panel bg-material p-1 shadow-popover backdrop-blur-[30px] backdrop-saturate-150"
        :style="{ zIndex }"
      >
        <ComboboxViewport class="max-h-64">
          <ComboboxEmpty class="px-2 py-4 text-center text-caption text-label-tertiary">
            {{ emptyText }}
          </ComboboxEmpty>
          <ComboboxItem
            v-for="item in items"
            :key="item.value"
            :value="item.value"
            :text-value="item.label"
            class="relative flex h-control cursor-default select-none items-center rounded-control pl-5 pr-2 text-body text-label outline-none data-[highlighted]:bg-accent data-[highlighted]:text-white"
          >
            <ComboboxItemIndicator class="absolute left-1.5">
              <Icon :as="Check" size="inline" />
            </ComboboxItemIndicator>
            <span class="truncate">{{ item.label }}</span>
          </ComboboxItem>
        </ComboboxViewport>
      </ComboboxContent>
    </ComboboxPortal>
  </ComboboxRoot>
</template>
