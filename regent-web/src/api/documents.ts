import { ApiError, apiUrl, get } from '@/api/http'
import type {
  ApiErrorBody,
  LibraryDocument,
  LibraryType,
  TaskDocument,
} from '@/types/api'
export { ACCEPTED_DOCUMENT_EXTENSIONS } from '@/utils/documentFiles'

export const MAX_DOCUMENT_BYTES = 25 * 1024 * 1024
export const MAX_DOCUMENTS_PER_TASK = 20

export type DocumentUploadOptions = {
  onProgress?: (percent: number) => void
  signal?: AbortSignal
}

export const listTaskDocuments = (taskId: number) =>
  get<TaskDocument[]>(`/tasks/${taskId}/documents`)

export const listFileLibrary = (projectId: number, type: LibraryType = 'GENERAL') =>
  get<LibraryDocument[]>('/file-library', { projectId, type })

export const bindLibraryDocuments = (taskId: number, documentIds: number[]) =>
  fetch(apiUrl(`/tasks/${taskId}/documents/from-library`), {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Regent-Relay-Request': '1',
    },
    body: JSON.stringify({ documentIds }),
  }).then(async (response) => {
    const body = await response.json().catch(() => ({}))
    if (!response.ok) {
      throw new ApiError(
        response.status,
        typeof body.code === 'string' ? body.code : 'UNKNOWN',
        typeof body.message === 'string' ? body.message : `绑定文件库失败（HTTP ${response.status}）`,
      )
    }
    return body as TaskDocument[]
  })

export function uploadTaskDocument(
  taskId: number,
  file: File,
  saveToLibrary: boolean,
  options: DocumentUploadOptions = {},
): Promise<TaskDocument> {
  return new Promise((resolve, reject) => {
    const form = new FormData()
    form.append('file', file)
    form.append('saveToLibrary', String(saveToLibrary))

    const xhr = new XMLHttpRequest()
    xhr.open('POST', apiUrl(`/tasks/${taskId}/documents`))
    xhr.setRequestHeader('Accept', 'application/json')
    xhr.setRequestHeader('X-Regent-Relay-Request', '1')

    xhr.upload.onprogress = (event) => {
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
          resolve(JSON.parse(xhr.responseText) as TaskDocument)
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
