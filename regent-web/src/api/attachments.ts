import { ApiError, get } from '@/api/http'
import type { ApiErrorBody, BugAttachment, TaskAttachment } from '@/types/api'

/*
 * 任务附件（图片）。
 *
 * 上传单独走 XMLHttpRequest 而不是 http.ts 的 fetch：进度条要有进度，就得读
 * xhr.upload.onprogress —— fetch 至今没有上传进度事件。错误处理照 http.ts 的形状再写一遍
 * （{code,message} → ApiError），否则同一类失败在两个出口下会长得不一样，
 * 调用点就得记住「这个接口的错误长这样、那个长那样」。
 */

/**
 * 单张图片的客户端上限，与后端 `regent.attachments.max-bytes` 的默认值一致。
 *
 * 这里拦一道只是为了不让 10MB 走完整个上传再被拒（手机流量是用户的）；
 * 真正的判据仍在后端 —— 前端拿到的 File 是浏览器说的，改小改大都拦不住。
 */
export const MAX_IMAGE_BYTES = 10 * 1024 * 1024

/** 收图白名单，与后端 ImageUploadPolicy 对齐。SVG 不在其中，理由见后端注释。 */
export const ACCEPTED_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp']

export const listTaskAttachments = (taskId: number) =>
  get<TaskAttachment[]>(`/tasks/${taskId}/attachments`)

export const listBugAttachments = (bugId: number) =>
  get<BugAttachment[]>(`/bugs/${bugId}/attachments`)

export type UploadOptions = {
  /** 0–100。仅在浏览器给出总长度时回调 */
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

export function uploadTaskAttachment(
  taskId: number,
  file: File,
  options: UploadOptions = {},
): Promise<TaskAttachment> {
  return uploadImage(`/api/tasks/${taskId}/attachments`, file, options)
}

export function uploadBugAttachment(
  bugId: number,
  file: File,
  options: UploadOptions = {},
): Promise<BugAttachment> {
  return uploadImage(`/api/bugs/${bugId}/attachments`, file, options)
}

function uploadImage<T>(
  endpoint: string,
  file: File,
  options: UploadOptions = {},
): Promise<T> {
  return new Promise((resolve, reject) => {
    const form = new FormData()
    // 字段名 file 与后端 @RequestParam("file") 是一对，改这里要同时改那边
    form.append('file', file)

    const xhr = new XMLHttpRequest()
    xhr.open('POST', endpoint)
    xhr.setRequestHeader('Accept', 'application/json')
    xhr.setRequestHeader('X-Regent-Relay-Request', '1')

    xhr.upload.onprogress = (event) => {
      // lengthComputable 为 false 时算不出比例：宁可不报，也不要让进度环停在假读数上
      if (event.lengthComputable && event.total > 0) {
        options.onProgress?.(Math.min(100, Math.round((event.loaded / event.total) * 100)))
      }
    }
    xhr.onerror = () =>
      reject(new ApiError(0, 'NETWORK', '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'))
    xhr.onabort = () => reject(new ApiError(0, 'ABORTED', '上传已取消'))
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText) as T)
        } catch {
          reject(new ApiError(xhr.status, 'UNKNOWN', `上传成功但响应读不懂（HTTP ${xhr.status}）`))
        }
        return
      }
      reject(parseError(xhr))
    }

    options.signal?.addEventListener('abort', () => xhr.abort(), { once: true })
    xhr.send(form)
  })
}

function parseError(xhr: XMLHttpRequest): ApiError {
  try {
    const body = JSON.parse(xhr.responseText) as ApiErrorBody
    return new ApiError(xhr.status, body.code, body.message)
  } catch {
    return new ApiError(xhr.status, 'UNKNOWN', `上传失败（HTTP ${xhr.status}）`)
  }
}
