import assert from 'node:assert/strict'
import test from 'node:test'

const { resolveDeploymentServerSelection } = await import(
  '../src/utils/deploymentServerSelection.ts'
)

const server = (id, projectId) => ({
  id,
  projectId,
  name: `server-${id}`,
  host: `${id}.example.com`,
  port: 22,
  username: 'root',
  authType: 'KEY',
  credentialConfigured: false,
  deploymentCount: 0,
  createdAt: '2026-10-19T00:00:00Z',
  updatedAt: '2026-10-19T00:00:00Z',
})

test('全部项目时优先选择确实有服务器的项目', () => {
  const selection = resolveDeploymentServerSelection(
    null,
    [{ id: 7 }, { id: 8 }],
    [server(31, 8), server(30, 8), server(12, 7)],
  )

  assert.equal(selection.projectId, 8)
  assert.deepEqual(selection.servers.map(({ id }) => id), [31, 30])
})

test('已经指定项目时保持项目边界并只返回该项目的服务器', () => {
  const selection = resolveDeploymentServerSelection(
    7,
    [{ id: 7 }, { id: 8 }],
    [server(31, 8), server(12, 7)],
  )

  assert.equal(selection.projectId, 7)
  assert.deepEqual(selection.servers.map(({ id }) => id), [12])
})

test('指定项目没有服务器时切换到实际有服务器的项目', () => {
  const selection = resolveDeploymentServerSelection(
    7,
    [{ id: 7 }, { id: 8 }],
    [server(31, 8), server(30, 8)],
  )

  assert.equal(selection.projectId, 8)
  assert.deepEqual(selection.servers.map(({ id }) => id), [31, 30])
})

test('全部项目且没有服务器时回退到第一个项目', () => {
  const selection = resolveDeploymentServerSelection(
    null,
    [{ id: 7 }, { id: 8 }],
    [],
  )

  assert.equal(selection.projectId, 7)
  assert.deepEqual(selection.servers, [])
})

test('项目尚未加载且没有服务器时保持空选择', () => {
  const selection = resolveDeploymentServerSelection(null, [], [])

  assert.equal(selection.projectId, null)
  assert.deepEqual(selection.servers, [])
})
