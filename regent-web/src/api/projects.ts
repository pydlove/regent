import { get, postJson } from '@/api/http'
import type { DirectoryListing, Project, ProjectGitBranch } from '@/types/api'
import type { DecisionLevel } from '@/types/task'

export type CreateProjectInput = {
  code: string
  name: string
  path: string
  defaultDecisionLevel: DecisionLevel
}

/** 全量项目列表。后端刻意不分页 —— 项目是平台里数量最少的东西。 */
export const listProjects = () => get<Project[]>('/projects')

/**
 * 注册项目。
 *
 * 后端会校验路径在不在本机（codex 要在那里跑），所以「路径写错」在这里就会失败，
 * 而不是等到第一次派发。失败原因原样透传给用户。
 */
export const createProject = (input: CreateProjectInput) => postJson<Project>('/projects', input)

/** 当前分支是工作区运行时状态，只在详情选中项目时查询。 */
export const getProjectGitBranch = (id: number) =>
  get<ProjectGitBranch>(`/projects/${id}/git-branch`)

/**
 * 列子目录，给注册表单的路径字段当选择器。
 *
 * 不传 path 就从后端用户的主目录开始。路径不存在、指向文件、或是相对路径时后端返 400 ——
 * 调用方按 ApiError 处理即可，不用自己再判断一遍。
 */
export const listDirectories = (path?: string) =>
  get<DirectoryListing>('/projects/directories', { path })
