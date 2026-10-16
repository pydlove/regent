import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { ApiError } from '@/api/http'
import { getRun } from '@/api/runs'
import { listTaskRuns } from '@/api/tasks'
import type { TaskRunDetail, TaskRunSummary } from '@/types/api'
import { runInProgress } from '@/types/task'

/*
 * 运行记录的数据源 —— 检查器里那块「这个任务跑过几次」。
 *
 * 三件事决定了它的形状：
 *   1. **按任务取**，不是全量。运行记录会一直攒下去，全量拉取迟早变成几万行。
 *   2. **执行中要自己重拉**。执行跑在后台线程上，界面不刷新就只能看到「执行中」三个字，
 *      而人真正想知道的是「跑到哪了」。刷新的节拍由视图控制（见 TasksView），
 *      因为「什么时候该停」取决于界面还在不在看这个任务。
 *   3. **列表与详情分开取**。事件流可能有上千行，点开某一次再取，别让列表把日志一起拖下来。
 */

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

export const useRunStore = defineStore('runs', () => {
  const taskId = ref<number | null>(null)
  const runs = ref<TaskRunSummary[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  const detail = ref<TaskRunDetail | null>(null)
  const detailLoading = ref(false)
  const detailError = ref<string | null>(null)

  /** 有任何一个在跑，就值得继续刷新。 */
  const hasRunning = computed(() => runs.value.some((run) => runInProgress(run.status)))

  const load = async (id: number) => {
    taskId.value = id
    loading.value = true
    error.value = null
    try {
      runs.value = await listTaskRuns(id)
      // 打开着的那次运行如果也在刷新，详情跟着更新，否则弹框里会停在一份过期的日志上
      if (detail.value && detail.value.run.taskId === id) {
        await refreshDetail(detail.value.run.id)
      }
    } catch (e) {
      error.value = messageOf(e)
      runs.value = []
    } finally {
      loading.value = false
    }
  }

  /** 静默重拉：自动刷新用，失败不覆盖上一次的结果，也不弹错。 */
  const refreshQuietly = async () => {
    const id = taskId.value
    if (id === null) return
    try {
      runs.value = await listTaskRuns(id)
      if (detail.value && detail.value.run.taskId === id) {
        await refreshDetail(detail.value.run.id)
      }
    } catch {
      // 自动刷新失败就等下一次节拍，界面保持上一次的样子
    }
  }

  const openRun = async (runId: number) => {
    detail.value = null
    detailError.value = null
    detailLoading.value = true
    try {
      detail.value = await getRun(runId)
    } catch (e) {
      detailError.value = messageOf(e)
    } finally {
      detailLoading.value = false
    }
  }

  const refreshDetail = async (runId: number) => {
    try {
      detail.value = await getRun(runId)
      detailError.value = null
    } catch {
      // 详情刷新失败保留旧内容，弹框里已有的事实比一句报错有用
    }
  }

  const closeRun = () => {
    detail.value = null
    detailError.value = null
  }

  const reset = () => {
    taskId.value = null
    runs.value = []
    error.value = null
    closeRun()
  }

  return {
    taskId,
    runs,
    loading,
    error,
    detail,
    detailLoading,
    detailError,
    hasRunning,
    load,
    refreshQuietly,
    openRun,
    closeRun,
    reset,
  }
})
