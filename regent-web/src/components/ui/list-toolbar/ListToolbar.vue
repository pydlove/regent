<!--
  菜单列表页的统一工具栏。

  宽屏固定为「标题 → 搜索与筛选 → 刷新与主操作」，容器不够宽时折成两行：
  第一行标题与操作，第二行搜索与筛选。页面只提供三个插槽，不再各自拼布局类，
  否则搜索框和新建按钮的先后顺序、宽度、断点会继续漂移。

  这里按工具栏自身宽度而不是浏览器宽度切换：任务详情侧栏展开后，即使窗口很宽，
  列表区也可能只剩约 1000px。按视口断点会把标题、筛选和操作强行塞进一行，
  最终把标题压成竖排，并让筛选标签与刷新按钮重叠。
-->
<template>
  <div class="list-toolbar-container shrink-0">
    <div
      class="list-toolbar flex min-h-toolbar flex-wrap items-center gap-x-3 gap-y-1.5 border-b border-separator px-3 py-1.5"
    >
      <div class="order-1 flex min-w-0 items-center gap-2">
        <slot name="title" />
      </div>

      <div
        v-if="$slots.actions"
        class="list-toolbar-actions order-2 ml-auto flex shrink-0 items-center gap-2"
      >
        <slot name="actions" />
      </div>

      <div
        v-if="$slots.filters"
        class="list-toolbar-filters order-3 flex w-full min-w-0 flex-wrap items-center gap-2"
      >
        <slot name="filters" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.list-toolbar-container {
  container-type: inline-size;
}

@container (min-width: 72rem) {
  .list-toolbar {
    height: var(--spacing-toolbar);
    flex-wrap: nowrap;
    padding-inline: 1.25rem;
    padding-block: 0;
  }

  .list-toolbar-actions {
    order: 3;
    margin-left: 0;
  }

  .list-toolbar-filters {
    order: 2;
    width: auto;
    margin-left: auto;
    flex-wrap: nowrap;
  }
}
</style>
