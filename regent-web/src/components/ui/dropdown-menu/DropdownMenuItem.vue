<script setup lang="ts">
import { DropdownMenuItem } from 'reka-ui'

withDefaults(defineProps<{ disabled?: boolean }>(), { disabled: false })

const emit = defineEmits<{ select: [] }>()
</script>

<template>
  <DropdownMenuItem
    :disabled="disabled"
    class="flex h-control cursor-default select-none items-center gap-2 rounded-control px-2 text-body text-label outline-none data-[highlighted]:bg-accent data-[highlighted]:text-white data-[disabled]:opacity-40"
    @select="emit('select')"
  >
    <slot />
  </DropdownMenuItem>
</template>
