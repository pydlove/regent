<script setup lang="ts">
/*
 * 对话框里的一行表单：左边右对齐的标签 + 右边的控件。
 *
 * 这是 macOS 对话框的字段惯例（Finder 的「新建文件夹」、钥匙串的「添加密码」、打印面板，
 * 全是这个排法），正好和网页表单「标签在上、控件在下」相反。所以它是个**对话框专用件**：
 * 页面里的表单在 macOS 上仍然是标签在上，别拿去复用。
 *
 * 标签用 13px 的主标签色，不是 11px 的次级色 —— 原生对话框里字段名是正文级的信息，
 * 不是辅助说明。辅助说明走 hint，落在控件下方（也是原生的位置）。
 */
withDefaults(
  defineProps<{
    label: string
    /** 控件下方的说明。它跟着控件走，不跟着标签走 —— 标签列塞不下整句话 */
    hint?: string
    /** 必填只做视觉提示，真正能不能提交仍由表单守卫决定。 */
    required?: boolean
    /** 标签列宽；默认 80px，长中文标签的专用弹框可以显式加宽。 */
    labelWidth?: string
  }>(),
  { labelWidth: 'w-20' },
)
</script>

<template>
  <div class="flex gap-3">
    <span :class="[labelWidth, 'shrink-0 whitespace-nowrap pt-1 text-right text-body text-label']">
      {{ label }}<span v-if="required" class="ml-0.5 text-danger" aria-hidden="true">*</span>
    </span>
    <div class="min-w-0 flex-1">
      <slot />
      <p v-if="hint" class="mt-1 text-caption text-label-tertiary">{{ hint }}</p>
    </div>
  </div>
</template>
