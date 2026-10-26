import type { DashboardData } from '@/types'

export class AccountScopedCache<T> {
  private readonly values = new Map<number, T>()

  set(userId: number, value: T): void {
    this.values.set(userId, value)
  }

  get(userId: number): T | undefined {
    return this.values.get(userId)
  }

  clear(): void {
    this.values.clear()
  }

  size(): number {
    return this.values.size
  }
}

export const dashboardCache = new AccountScopedCache<DashboardData>()
