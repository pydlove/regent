import assert from 'node:assert/strict'
import test from 'node:test'

const {
  deprecatedProjectMemberRoles,
  projectMemberRoleLabels,
  projectMemberRoleOptions,
  workflowRoleOptions,
} = await import('../src/utils/projectMemberRoles.ts')

test('项目角色同时提供测试负责人和测试人员', () => {
  assert.equal(projectMemberRoleLabels.BUSINESS_OWNER, '业务负责人')
  assert.equal(projectMemberRoleLabels.QA, '测试负责人')
  assert.equal(projectMemberRoleLabels.TESTER, '测试人员')

  const optionValues = projectMemberRoleOptions.map(({ value }) => value)
  assert.equal(optionValues.includes('BUSINESS_OWNER'), false)
  assert.equal(optionValues.includes('QA'), true)
  assert.equal(optionValues.includes('TESTER'), true)
})

test('业务负责人及冗余角色不再作为新分配角色', () => {
  const optionValues = projectMemberRoleOptions.map(({ value }) => value)
  assert.deepEqual(deprecatedProjectMemberRoles, [
    'BUSINESS_OWNER',
    'OPS',
    'SECURITY',
    'RELEASE_MANAGER',
    'VIEWER',
    'AUDITOR',
  ])
  for (const role of deprecatedProjectMemberRoles) {
    assert.equal(optionValues.includes(role), false)
  }
  assert.equal(workflowRoleOptions.some(({ value }) => value === 'OWNER'), true)
  assert.equal(workflowRoleOptions.some(({ value }) => value === 'RELEASE_MANAGER'), false)
})
