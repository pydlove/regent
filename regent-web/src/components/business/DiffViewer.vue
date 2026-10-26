<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    content: string
    legend?: boolean
    scrollable?: boolean
  }>(),
  { legend: false, scrollable: true },
)

type DiffKind = 'meta' | 'hunk' | 'addition' | 'deletion' | 'context'

type RenderedLine = {
  kind: DiffKind
  oldLine: number | null
  newLine: number | null
  text: string
}

const lines = computed<RenderedLine[]>(() => {
  let oldLine = 0
  let newLine = 0
  let inHunk = false
  return props.content.split('\n').map((text) => {
    const hunk = text.match(/^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/)
    if (hunk) {
      oldLine = Number(hunk[1])
      newLine = Number(hunk[2])
      inHunk = true
      return { kind: 'hunk', oldLine: null, newLine: null, text }
    }
    if (text.startsWith('diff --git ') || text.startsWith('index ')) {
      inHunk = false
      return { kind: 'meta', oldLine: null, newLine: null, text }
    }
    if (text.startsWith('--- ') || text.startsWith('+++ ')) {
      return { kind: 'meta', oldLine: null, newLine: null, text }
    }
    if (text.startsWith('+')) {
      const current = newLine++
      return { kind: 'addition', oldLine: null, newLine: current, text }
    }
    if (text.startsWith('-')) {
      const current = oldLine++
      return { kind: 'deletion', oldLine: current, newLine: null, text }
    }
    if (inHunk) {
      const currentOld = oldLine++
      const currentNew = newLine++
      return { kind: 'context', oldLine: currentOld, newLine: currentNew, text }
    }
    return { kind: 'meta', oldLine: null, newLine: null, text }
  })
})

const additions = computed(() => lines.value.filter((line) => line.kind === 'addition').length)
const deletions = computed(() => lines.value.filter((line) => line.kind === 'deletion').length)

function lineClass(kind: DiffKind): string {
  return {
    meta: 'text-label-tertiary',
    hunk: 'bg-accent/8 text-accent',
    addition: 'bg-success/8 text-label',
    deletion: 'bg-danger/8 text-label',
    context: 'text-label-secondary',
  }[kind]
}

function marker(kind: DiffKind, text: string): string {
  if (kind === 'addition' || kind === 'deletion') return text.slice(0, 1)
  return ''
}
</script>

<template>
  <div
    class="flex flex-col"
    :class="scrollable ? 'h-full min-h-0 overflow-hidden' : ''"
  >
    <div
      v-if="legend"
      class="flex shrink-0 items-center gap-3 border-b border-separator-soft px-3 py-1.5 text-caption"
    >
      <span class="inline-flex items-center gap-1 text-success">
        <span class="size-2 rounded-sm bg-success/50" />+{{ additions }}
      </span>
      <span class="inline-flex items-center gap-1 text-danger">
        <span class="size-2 rounded-sm bg-danger/50" />-{{ deletions }}
      </span>
      <span class="ml-auto text-label-tertiary">双列行号 · 横向滚动</span>
    </div>
    <div
      class="scroll-area"
      :class="scrollable ? 'min-h-0 flex-1 overflow-auto' : 'overflow-visible'"
    >
      <div class="min-w-max py-1 font-mono text-caption leading-[1.6]">
        <div
          v-for="(line, index) in lines"
          :key="index"
          class="grid min-h-5 grid-cols-[3.5rem_3.5rem_1rem_minmax(0,1fr)] border-l-2"
          :class="[
            lineClass(line.kind),
            line.kind === 'addition'
              ? 'border-success/60'
              : line.kind === 'deletion'
                ? 'border-danger/60'
                : line.kind === 'hunk'
                  ? 'border-accent/60'
                  : 'border-transparent',
          ]"
        >
          <span class="select-none border-r border-separator-soft/60 px-1.5 text-right text-label-tertiary/70">
            {{ line.oldLine ?? '' }}
          </span>
          <span class="select-none border-r border-separator-soft/60 px-1.5 text-right text-label-tertiary/70">
            {{ line.newLine ?? '' }}
          </span>
          <span class="select-none text-center">{{ marker(line.kind, line.text) }}</span>
          <code class="whitespace-pre pr-3">{{ line.text }}</code>
        </div>
      </div>
    </div>
  </div>
</template>
