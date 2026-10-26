import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

import {
  createBug,
  listBugs,
  markBugFixed,
  requestBugFix,
  verifyBug,
  wontFixBug,
  type BugScope,
  type CreateBugInput,
} from '@/api/bugs'
import { ApiError } from '@/api/http'
import type { Bug } from '@/types/bug'

/*
 * 缺陷单。和 useTaskStore 同一套写法：迁移收在方法里，方法开头先过守卫。
 *
 * 列表来自真实接口。分页与筛选下推 SQL；创建成功后重新拉取，不用本地临时行冒充落库结果。
 *
 * 「派生修复任务」由后端应用服务编排；这里只负责调用接口并重新拉取真实状态。
 */

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

export const useBugStore = defineStore('bugs', () => {
  const bugs = ref<Bug[]>([])
  const total = ref(0)
  const openTotal = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const scope = ref<BugScope>('OPEN')
  const keyword = ref('')

  const loading = ref(false)
  const error = ref<string | null>(null)

  const byId = (id: number) => {
    const bug = bugs.value.find((b) => b.id === id)
    if (!bug) throw new Error(`缺陷单不存在：${id}`)
    return bug
  }

  const load = async () => {
    loading.value = true
    error.value = null
    try {
      const result = await listBugs({
        scope: scope.value,
        keyword: keyword.value,
        page: page.value,
        size: pageSize.value,
      })
      bugs.value = result.items
      total.value = result.total
      if (result.totalPages > 0 && page.value > result.totalPages) {
        page.value = result.totalPages
      }
    } catch (e) {
      error.value = messageOf(e)
      bugs.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  const loadOpenCount = async () => {
    try {
      const result = await listBugs({ scope: 'OPEN', page: 1, size: 1 })
      openTotal.value = result.total
    } catch {
      // 角标是补充信息，取不到不影响 Bug 列表本身。
    }
  }

  const createManual = async (input: CreateBugInput) => {
    const created = await createBug(input)
    await Promise.all([load(), loadOpenCount()])
    return created
  }

  const requestFix = async (id: number) => {
    const updated = await requestBugFix(id)
    await Promise.all([load(), loadOpenCount()])
    return updated
  }

  const markFixed = async (id: number) => {
    const updated = await markBugFixed(id)
    await Promise.all([load(), loadOpenCount()])
    return updated
  }

  const verify = async (id: number) => {
    const updated = await verifyBug(id)
    await Promise.all([load(), loadOpenCount()])
    return updated
  }

  const wontFix = async (id: number, reason?: string) => {
    const updated = await wontFixBug(id, reason)
    await Promise.all([load(), loadOpenCount()])
    return updated
  }

  watch(scope, () => {
    page.value = 1
    void load()
  })
  watch([page, pageSize], () => {
    void load()
  })
  watch(keyword, () => {
    page.value = 1
    void load()
  })

  return {
    bugs,
    total,
    openTotal,
    page,
    pageSize,
    scope,
    keyword,
    loading,
    error,
    byId,
    load,
    loadOpenCount,
    createManual,
    requestFix,
    markFixed,
    verify,
    wontFix,
  }
})
