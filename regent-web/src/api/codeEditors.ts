import { get, postJsonNoContent } from '@/api/http'
import type { CodeEditor } from '@/types/api'

export const getAvailableCodeEditors = (projectId: number) =>
  get<CodeEditor[]>(`/projects/${projectId}/code-editors`)

export const openProjectCode = (
  projectId: number,
  editorId: CodeEditor['id'],
  relativePath: string | null,
  line: number | null,
) =>
  postJsonNoContent(
    `/projects/${projectId}/code-editors/${editorId}/open`,
    { relativePath, line },
  )
