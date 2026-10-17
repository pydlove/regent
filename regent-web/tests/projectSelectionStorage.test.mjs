import assert from 'node:assert/strict'
import test from 'node:test'

const {
  PROJECT_SELECTION_STORAGE_KEY,
  readProjectSelection,
  writeProjectSelection,
} = await import('../src/utils/projectSelectionStorage.ts')

function memoryStorage(initial = {}) {
  const values = new Map(Object.entries(initial))
  return {
    getItem(key) {
      return values.get(key) ?? null
    },
    removeItem(key) {
      values.delete(key)
    },
    setItem(key, value) {
      values.set(key, value)
    },
  }
}

test('刷新后从 localStorage 恢复顶栏项目', () => {
  const storage = memoryStorage()

  writeProjectSelection(42, storage)

  assert.equal(readProjectSelection(storage), 42)
})

test('清理非法项目 ID，避免刷新后带着无效筛选', () => {
  const storage = memoryStorage({ [PROJECT_SELECTION_STORAGE_KEY]: 'not-a-number' })

  assert.equal(readProjectSelection(storage), null)
  assert.equal(storage.getItem(PROJECT_SELECTION_STORAGE_KEY), null)
})

test('选择全部项目时删除持久化值', () => {
  const storage = memoryStorage({ [PROJECT_SELECTION_STORAGE_KEY]: '42' })

  writeProjectSelection(null, storage)

  assert.equal(storage.getItem(PROJECT_SELECTION_STORAGE_KEY), null)
})

test('持久化写入失败不阻断当前筛选', () => {
  const storage = {
    getItem() {
      return null
    },
    removeItem() {
      throw new Error('storage disabled')
    },
    setItem() {
      throw new Error('storage disabled')
    },
  }

  assert.doesNotThrow(() => writeProjectSelection(42, storage))
  assert.doesNotThrow(() => writeProjectSelection(null, storage))
})
