import { computed, ref, toValue, watch, type MaybeRefOrGetter, type WatchSource } from 'vue'

/*
 * 分页状态。任务 / 决策 / Bug 单三个列表共用一份，所以从视图里抽出来。
 *
 * 它**只管页码**，不管数据从哪来：传进来的是「已经筛选好的全量列表」，出去的是「当前这一页」。
 * 接后端时这里换成 `?page=1&size=20`，视图一行都不用动 —— 这也是抽它的唯一理由。
 *
 * 两条边界是分页最容易漏、漏了又没有报错的地方：
 *   1. 筛选条件变了要回第 1 页。停在原页码上会得到一张空表，而且看不出为什么空；
 *   2. 总数变少（筛掉、驳回、关闭）时当前页可能越界。不夹回去，用户会看着一张空表
 *      以为数据没了。
 */
export function usePagination<T>(
  source: MaybeRefOrGetter<T[]>,
  options: { pageSize?: number; resetOn?: WatchSource | WatchSource[] } = {},
) {
  const pageSize = ref(options.pageSize ?? 20)
  const page = ref(1)

  const total = computed(() => toValue(source).length)
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

  const items = computed(() => {
    const start = (page.value - 1) * pageSize.value
    return toValue(source).slice(start, start + pageSize.value)
  })

  // 1–20 这种区间用 rangeStart/rangeEnd，不要用「page * size」现算：
  // 最后一页不满时算出来的结束值会大于总数。
  const rangeStart = computed(() => (total.value === 0 ? 0 : (page.value - 1) * pageSize.value + 1))
  const rangeEnd = computed(() => Math.min(page.value * pageSize.value, total.value))

  const canPrev = computed(() => page.value > 1)
  const canNext = computed(() => page.value < pageCount.value)

  watch(pageCount, (count) => {
    if (page.value > count) page.value = count
  })

  watch(pageSize, () => {
    page.value = 1
  })

  if (options.resetOn) {
    watch(options.resetOn, () => {
      page.value = 1
    })
  }

  const prev = () => {
    if (canPrev.value) page.value -= 1
  }
  const next = () => {
    if (canNext.value) page.value += 1
  }

  return {
    page,
    pageSize,
    total,
    pageCount,
    items,
    rangeStart,
    rangeEnd,
    canPrev,
    canNext,
    prev,
    next,
  }
}
