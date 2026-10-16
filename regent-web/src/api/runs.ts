import { get } from '@/api/http'
import type { TaskRunDetail } from '@/types/api'

/*
 * 执行记录单独一个出口，因为它的路径也是单独的：`/api/runs/{runId}`。
 * 运行记录有自己的 ID 空间（前端点开某一次运行拿的就是 runId），
 * 挂在 /tasks 下面会让 URL 里多一个查询里用不上的 taskId。
 */

/** 一次尝试的详情：列表行的字段 + 末条消息原文 + 完整事件流。 */
export const getRun = (runId: number) => get<TaskRunDetail>(`/runs/${runId}`)
