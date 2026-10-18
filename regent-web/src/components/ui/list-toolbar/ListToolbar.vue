<!--
  菜单列表页的统一工具栏。

  桌面端固定为「标题 → 搜索与筛选 → 刷新与主操作」，低于 xl 折成两行：
  第一行标题与操作，第二行搜索与筛选。页面只提供三个插槽，不再各自拼布局类，
  否则搜索框和新建按钮的先后顺序、宽度、断点会继续漂移。
-->
<template>
  <div
    class="flex min-h-toolbar shrink-0 flex-wrap items-center gap-x-3 gap-y-1.5 border-b border-separator px-3 py-1.5 xl:h-toolbar xl:flex-nowrap xl:px-5 xl:py-0"
  >
    <div class="order-1 flex min-w-0 items-center gap-2">
      <slot name="title" />
    </div>

    <div
      v-if="$slots.actions"
      class="order-2 ml-auto flex shrink-0 items-center gap-2 xl:order-3 xl:ml-0"
    >
      <slot name="actions" />
    </div>

    <div
      v-if="$slots.filters"
      class="order-3 flex w-full min-w-0 flex-wrap items-center gap-2 xl:order-2 xl:ml-auto xl:w-auto xl:flex-nowrap"
    >
      <slot name="filters" />
    </div>
  </div>
</template>
