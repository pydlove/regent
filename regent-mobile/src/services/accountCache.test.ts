import { describe, expect, it } from 'vitest'

import { AccountScopedCache } from './accountCache'

describe('AccountScopedCache', () => {
  it('never returns account A data to account B', () => {
    const cache = new AccountScopedCache<{ task: string }>()
    cache.set(1, { task: 'A 的任务' })
    cache.set(2, { task: 'B 的任务' })

    expect(cache.get(1)?.task).toBe('A 的任务')
    expect(cache.get(2)?.task).toBe('B 的任务')
    expect(cache.get(3)).toBeUndefined()
  })

  it('clears all account scopes when switching users', () => {
    const cache = new AccountScopedCache<string>()
    cache.set(1, 'A')
    cache.set(2, 'B')

    cache.clear()

    expect(cache.size()).toBe(0)
    expect(cache.get(1)).toBeUndefined()
    expect(cache.get(2)).toBeUndefined()
  })
})
