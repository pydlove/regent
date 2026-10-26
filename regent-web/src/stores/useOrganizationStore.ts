import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

import {
  createOrganization,
  listOrganizations,
  updateOrganization,
} from '@/api/organizations'
import type {
  Organization,
  OrganizationInput,
  OrganizationRole,
} from '@/types/organization'

const STORAGE_KEY = 'regent.currentOrganizationId'

function readStoredOrganizationId(): number | null {
  try {
    const value = Number(localStorage.getItem(STORAGE_KEY))
    return Number.isSafeInteger(value) && value > 0 ? value : null
  } catch {
    return null
  }
}

function writeStoredOrganizationId(value: number | null): void {
  try {
    if (value === null) localStorage.removeItem(STORAGE_KEY)
    else localStorage.setItem(STORAGE_KEY, String(value))
  } catch {
    // 浏览器禁用存储时只影响下次默认组织，不阻断当前使用。
  }
}

export const useOrganizationStore = defineStore('organization', () => {
  const organizations = ref<Organization[]>([])
  const currentId = ref<number | null>(readStoredOrganizationId())
  const loading = ref(false)
  const error = ref<string | null>(null)

  const current = computed(
    () => organizations.value.find((item) => item.id === currentId.value) ?? null,
  )
  const currentRoles = computed<OrganizationRole[]>(() => current.value?.currentUserRoles ?? [])
  const canManageOrganization = computed(() =>
    currentRoles.value.some((role) => role === 'ORG_OWNER' || role === 'ORG_ADMIN'),
  )
  const canManageProjects = computed(
    () => canManageOrganization.value || currentRoles.value.includes('PROJECT_MANAGER'),
  )
  const canAccessManagement = computed(() => canManageProjects.value)

  const resolveCurrent = (items: Organization[]): number | null => {
    const candidate = currentId.value
    if (candidate && items.some((item) => item.id === candidate)) return candidate
    const stored = readStoredOrganizationId()
    if (stored && items.some((item) => item.id === stored)) return stored
    return items[0]?.id ?? null
  }

  const load = async (): Promise<void> => {
    const previousOrganizations = organizations.value
    const previousCurrentId = currentId.value
    loading.value = true
    error.value = null
    try {
      organizations.value = await listOrganizations()
      currentId.value = resolveCurrent(organizations.value)
      writeStoredOrganizationId(currentId.value)
    } catch (value) {
      organizations.value = previousOrganizations
      currentId.value = previousCurrentId
      error.value = value instanceof Error ? value.message : '组织列表加载失败'
      throw value
    } finally {
      loading.value = false
    }
  }

  const select = (id: number): void => {
    if (!organizations.value.some((item) => item.id === id)) return
    currentId.value = id
  }

  const create = async (input: OrganizationInput): Promise<Organization> => {
    const created = await createOrganization(input)
    await load()
    currentId.value = created.id
    writeStoredOrganizationId(created.id)
    return created
  }

  const update = async (
    id: number,
    input: Omit<OrganizationInput, 'code'>,
  ): Promise<Organization> => {
    const updated = await updateOrganization(id, input)
    const index = organizations.value.findIndex((item) => item.id === id)
    if (index >= 0) organizations.value[index] = updated
    return updated
  }

  const clear = (): void => {
    organizations.value = []
    currentId.value = null
    error.value = null
    writeStoredOrganizationId(null)
  }

  watch(currentId, (value) => writeStoredOrganizationId(value))

  return {
    organizations,
    currentId,
    loading,
    error,
    current,
    currentRoles,
    canManageOrganization,
    canManageProjects,
    canAccessManagement,
    load,
    select,
    create,
    update,
    clear,
  }
})
