import { deleteRequest, get, post, postJson, putJson } from '@/api/http'
import type {
  TaskTestCaseBinding,
  TestCaseBatchRunResult,
  TestCaseDetail,
  TestCaseRunDetail,
  TestCaseRunSummary,
  TestCaseSummary,
} from '@/types/api'

export type SaveTestCaseInput = {
  projectId: number | null
  name: string
  type: string
  description: string
  command: string
  scriptFilename: string | null
  scriptContent: string | null
  enabled: boolean
}

export const listTestCases = (projectId?: number | null) =>
  get<TestCaseSummary[]>('/test-cases', { projectId })

export const getTestCase = (id: number) => get<TestCaseDetail>(`/test-cases/${id}`)

export const createTestCase = (input: SaveTestCaseInput) =>
  postJson<TestCaseDetail>('/test-cases', input)

export const updateTestCase = (id: number, input: SaveTestCaseInput) =>
  putJson<TestCaseDetail>(`/test-cases/${id}`, input)

export const setTestCaseEnabled = (id: number, enabled: boolean) =>
  putJson<TestCaseDetail>(`/test-cases/${id}/enabled`, { enabled })

export const deleteTestCase = (id: number) => deleteRequest(`/test-cases/${id}`)

export const listTestCaseRuns = (id: number, limit = 30) =>
  get<TestCaseRunSummary[]>(`/test-cases/${id}/runs`, { limit })

export const runTestCase = (id: number) =>
  post<TestCaseRunSummary>(`/test-cases/${id}/run`)

export const runTestCases = (testCaseIds: number[]) =>
  postJson<TestCaseBatchRunResult>('/test-cases/run', { testCaseIds })

export const getTestCaseRun = (id: number) =>
  get<TestCaseRunDetail>(`/test-case-runs/${id}`)

export const listTaskTestCases = (taskId: number) =>
  get<TaskTestCaseBinding[]>(`/tasks/${taskId}/test-cases`)

export const bindTaskTestCases = (taskId: number, testCaseIds: number[]) =>
  putJson<TaskTestCaseBinding[]>(`/tasks/${taskId}/test-cases`, { testCaseIds })

export const listTaskTestCaseRuns = (taskId: number) =>
  get<TestCaseRunSummary[]>(`/tasks/${taskId}/test-case-runs`)
