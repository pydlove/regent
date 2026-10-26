import assert from 'node:assert/strict'
import test from 'node:test'

const {
  formatRequirementNo,
  lockedRequirementVersionIds,
  requirementDisplayStatus,
  versionHasStartedDesign,
} = await import('../src/utils/requirementPool.ts')

test('需求编号至少六位，超过六位后自然扩位', () => {
  assert.equal(formatRequirementNo(1), '000001')
  assert.equal(formatRequirementNo(999999), '999999')
  assert.equal(formatRequirementNo(1_000_000), '1000000')
  assert.equal(formatRequirementNo(12_345_678), '12345678')
  assert.equal(formatRequirementNo(null), '—')
})

test('需求池展示状态同时取决于评审状态和版本关联', () => {
  assert.equal(requirementDisplayStatus({ poolStatus: 'POOL', versionId: null }), 'PENDING_VERSION')
  assert.equal(requirementDisplayStatus({ poolStatus: 'POOL', versionId: 12 }), 'PENDING_REVIEW')
  assert.equal(requirementDisplayStatus({ poolStatus: 'IN_REVIEW', versionId: 12 }), 'IN_REVIEW')
  assert.equal(requirementDisplayStatus({ poolStatus: 'FORMAL', versionId: 12 }), 'FORMAL')
  assert.equal(requirementDisplayStatus({ poolStatus: 'REJECTED', versionId: 12 }), 'REJECTED')
})

test('正式需求所在版本视为已进入设计阶段，不能再追加需求', () => {
  const requirements = [
    { poolStatus: 'FORMAL', versionId: 12 },
    { poolStatus: 'FORMAL', versionId: 12 },
    { poolStatus: 'IN_REVIEW', versionId: 13 },
    { poolStatus: 'POOL', versionId: 14 },
  ]

  assert.deepEqual([...lockedRequirementVersionIds(requirements)], [12])
  assert.equal(versionHasStartedDesign(requirements, 12), true)
  assert.equal(versionHasStartedDesign(requirements, 13), false)
  assert.equal(versionHasStartedDesign(requirements, 14), false)
  assert.equal(versionHasStartedDesign(requirements, null), false)
})
