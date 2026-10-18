import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { approveDecision, listDecisions, rejectDecision } from '@/api/decisions'
import type { DecisionSummary } from '@/types/api'

/*
 * 决策列表的数据源。
 *
 * 「待批」与「已决」是两条独立的查询（scope=PENDING / RESOLVED），不是把全量拉到前端再过滤：
 * 已决的决策会一直攒下去，全量拉取迟早会变成一次几万行的请求。
 *
 * 角标数字单独维护（pendingTotal）：它要在侧边栏上显示，而侧边栏与决策页的生命周期不同步 ——
 * 直接复用列表的 total 会让「切到已决 tab 之后，侧边栏角标变成已决数」。
 */

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

export type DecisionScope = 'PENDING' | 'RESOLVED'

export const useDecisionStore = defineStore('decisions', () => {
  const items = ref<DecisionSummary[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const scope = ref<DecisionScope>('PENDING')
  const keyword = ref('')

  const loading = ref(false)
  const error = ref<string | null>(null)

  const pendingTotal = ref(0)

  const load = async () => {
    loading.value = true
    error.value = null
    try {
      const result = await listDecisions({
        scope: scope.value,
        keyword: keyword.value,
        page: page.value,
        size: pageSize.value,
      })
      items.value = result.items
      total.value = result.total
      if (result.totalPages > 0 && page.value > result.totalPages) {
        page.value = result.totalPages
      }
    } catch (e) {
      error.value = messageOf(e)
      items.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  const loadPendingCount = async () => {
    try {
      const result = await listDecisions({ scope: 'PENDING', page: 1, size: 1 })
      pendingTotal.value = result.total
    } catch {
      // 角标取不到就不显示，不影响主流程
    }
  }

  /**
   * 批准或驳回。返回 null 表示成功，否则是给用户看的失败原因。
   *
   * <p>失败原因原样透传后端的 message：它是从聚合守卫里抛出来的，说的是「为什么这条决策
   * 现在批不了」（已决议、任务不在阻塞中、令牌过期……）。在这里换成一句笼统的
   * 「操作失败」，等于把唯一能解释清楚的信息丢掉。
   */
  const review = async (id: number, mode: 'approve' | 'reject', note: string): Promise<string | null> => {
    try {
      if (mode === 'approve') {
        await approveDecision(id, { note })
      } else {
        await rejectDecision(id, { note })
      }
      // 决议完立刻重拉：待批列表少一条、侧边栏角标同步减一，两处都从同一个源来
      await Promise.all([load(), loadPendingCount()])
      return null
    } catch (e) {
      return messageOf(e)
    }
  }

  watch([scope, keyword, page, pageSize], () => {
    void load()
  })

  return {
    items,
    total,
    page,
    pageSize,
    scope,
    keyword,
    loading,
    error,
    pendingTotal,
    load,
    loadPendingCount,
    review,
  }
})
