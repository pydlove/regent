<script setup lang="ts">
/*
 * 列表里的「小色点 + 文字」。不用彩色胶囊 —— 那是 Web 后台的习惯，
 * 苹果在 Finder / 邮件 / 系统设置里一律是小色点，颜色只是辅助，文字才是信息。
 *
 * 色点和文字分开传：色点由业务组件按枚举映射，文字用默认插槽，
 * 于是「整行被选中时文字要跟着变白」这件事自动成立（文字用 inherit）。
 */
defineProps<{ dot: string }>()
</script>

<template>
  <span class="inline-flex items-center gap-1.5 whitespace-nowrap">
    <!--
      整行被选中时底色是实心强调色，蓝点的蓝会直接糊进蓝底（描边救不了，环太细）。
      所以选中态把点整个转成白色 —— 这也是系统列表里的做法。
    -->
    <span :class="['size-1.5 shrink-0 rounded-full group-data-[selected=true]:bg-white', dot]" />
    <span><slot /></span>
  </span>
</template>
