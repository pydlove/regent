import { deleteRequest, get, postJson, putJson } from '@/api/http'
import type { ProductVersion } from '@/types/delivery'

export type ProductVersionInput = {
  projectId: number
  versionNo: string
  description: string
}

export const listProductVersions = (projectId: number) =>
  get<ProductVersion[]>('/product-versions', { projectId })

export const createProductVersion = (input: ProductVersionInput) =>
  postJson<ProductVersion>('/product-versions', input)

export const updateProductVersion = (
  id: number,
  input: { description: string },
) => putJson<ProductVersion>(`/product-versions/${id}`, input)

export const closeProductVersion = (id: number) =>
  postJson<ProductVersion>(`/product-versions/${id}/close`, {})

export const deleteProductVersion = (id: number) =>
  deleteRequest(`/product-versions/${id}`)
