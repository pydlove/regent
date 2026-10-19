<script setup lang="ts">
import type { InlineToken } from '@/utils/markdown'

defineOptions({ name: 'MarkdownInline' })

defineProps<{
  tokens: InlineToken[]
}>()
</script>

<template>
  <template v-for="(token, index) in tokens" :key="index">
    <br v-if="token.type === 'break'" />
    <code
      v-else-if="token.type === 'code'"
      class="rounded-badge bg-window px-1 py-0.5 font-mono text-caption text-label"
    >{{ token.text }}</code>
    <strong v-else-if="token.type === 'strong'" class="font-emphasis text-label">
      <MarkdownInline :tokens="token.children" />
    </strong>
    <em v-else-if="token.type === 'emphasis'" class="italic">
      <MarkdownInline :tokens="token.children" />
    </em>
    <s v-else-if="token.type === 'strike'" class="text-label-tertiary">
      <MarkdownInline :tokens="token.children" />
    </s>
    <a
      v-else-if="token.type === 'link'"
      :href="token.href"
      :target="token.href.startsWith('#') ? undefined : '_blank'"
      :rel="token.href.startsWith('#') ? undefined : 'noopener noreferrer'"
      class="break-all text-accent hover:underline"
    >
      <MarkdownInline :tokens="token.children" />
    </a>
    <span v-else-if="token.type === 'text'">{{ token.text }}</span>
  </template>
</template>
