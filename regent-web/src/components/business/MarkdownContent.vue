<script setup lang="ts">
import { computed } from 'vue'

import MarkdownInline from '@/components/business/MarkdownInline.vue'
import { parseInline, parseMarkdown } from '@/utils/markdown'

const props = defineProps<{
  markdown: string
}>()

const blocks = computed(() => parseMarkdown(props.markdown))

const headingClasses: Record<number, string> = {
  1: 'text-title font-emphasis',
  2: 'text-body font-emphasis',
  3: 'text-body font-emphasis',
  4: 'text-caption font-emphasis',
  5: 'text-caption font-emphasis',
  6: 'text-caption font-emphasis',
}
</script>

<template>
  <article class="space-y-3 break-words text-body leading-relaxed text-label-secondary">
    <template v-for="(block, index) in blocks" :key="index">
      <component
        :is="`h${block.level}`"
        v-if="block.type === 'heading'"
        :class="[headingClasses[block.level], 'text-label']"
      >
        <MarkdownInline :tokens="parseInline(block.content)" />
      </component>

      <p v-else-if="block.type === 'paragraph'" class="whitespace-pre-wrap">
        <MarkdownInline :tokens="parseInline(block.content)" />
      </p>

      <ul v-else-if="block.type === 'list' && !block.ordered" class="list-disc space-y-1 pl-5">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
          <MarkdownInline :tokens="parseInline(item)" />
        </li>
      </ul>
      <ol v-else-if="block.type === 'list'" class="list-decimal space-y-1 pl-5">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
          <MarkdownInline :tokens="parseInline(item)" />
        </li>
      </ol>

      <div v-else-if="block.type === 'code'" class="overflow-hidden rounded-panel bg-window">
        <div v-if="block.language" class="border-b border-separator px-3 py-1.5 font-mono text-caption text-label-tertiary">
          {{ block.language }}
        </div>
        <pre class="scroll-area overflow-x-auto p-3 font-mono text-caption leading-relaxed text-label"><code>{{ block.content }}</code></pre>
      </div>

      <blockquote
        v-else-if="block.type === 'quote'"
        class="rounded-r-panel border-l-2 border-accent/60 bg-accent/5 px-3 py-2 text-label-secondary"
      >
        <MarkdownInline :tokens="parseInline(block.content)" />
      </blockquote>

      <hr v-else-if="block.type === 'rule'" class="border-separator" />

      <div v-else-if="block.type === 'table'" class="scroll-area overflow-x-auto rounded-panel border border-separator">
        <table class="w-full min-w-max border-collapse text-left text-caption">
          <thead class="bg-window">
            <tr>
              <th
                v-for="(header, headerIndex) in block.headers"
                :key="headerIndex"
                class="border-b border-separator px-3 py-2 font-emphasis text-label"
              >
                <MarkdownInline :tokens="parseInline(header)" />
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex" class="border-b border-separator-soft last:border-b-0">
              <td
                v-for="(cell, cellIndex) in row"
                :key="cellIndex"
                class="px-3 py-2 align-top text-label-secondary"
              >
                <MarkdownInline :tokens="parseInline(cell)" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </article>
</template>
