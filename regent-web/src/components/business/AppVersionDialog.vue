<script setup lang="ts">
import { Rocket } from '@lucide/vue'
import { ref } from 'vue'

import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import { APP_VERSION, CURRENT_RELEASE } from '@/content/releases'

const open = ref(false)
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="版本更新"
    :description="`${CURRENT_RELEASE.version} · ${CURRENT_RELEASE.label}`"
    :icon="Rocket"
  >
    <template #trigger>
      <button
        type="button"
        class="tnum rounded-sm text-caption text-label-tertiary transition-colors duration-100 ease-apple hover:text-label-secondary hover:underline hover:underline-offset-2 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
        :title="`查看 ${APP_VERSION} 的主要更新内容`"
      >
        {{ APP_VERSION }}
      </button>
    </template>

    <div class="rounded-panel bg-content/70 px-3 py-2.5">
      <div class="flex items-center gap-2">
        <Badge tone="accent">{{ CURRENT_RELEASE.label }}</Badge>
        <span class="tnum text-caption text-label-tertiary">{{ CURRENT_RELEASE.releasedAt }}</span>
      </div>
      <p class="mt-2 text-caption leading-relaxed text-label-secondary">
        {{ CURRENT_RELEASE.summary }}
      </p>
    </div>

    <p class="mt-4 text-caption font-emphasis text-label-secondary">主要更新</p>
    <ul class="mt-3 grid gap-x-6 gap-y-3 sm:grid-cols-2">
      <li
        v-for="item in CURRENT_RELEASE.highlights"
        :key="item.title"
        class="flex gap-2"
      >
        <span class="mt-[6px] size-1.5 shrink-0 rounded-full bg-accent" aria-hidden="true" />
        <span class="min-w-0">
          <span class="block text-caption font-emphasis text-label">{{ item.title }}</span>
          <span class="mt-0.5 block text-caption leading-relaxed text-label-secondary">
            {{ item.description }}
          </span>
        </span>
      </li>
    </ul>

    <template #footer>
      <Button size="sm" @click="open = false">关闭</Button>
    </template>
  </Dialog>
</template>
