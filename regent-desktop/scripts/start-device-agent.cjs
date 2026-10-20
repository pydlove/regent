#!/usr/bin/env node
const os = require('node:os')
const path = require('node:path')
const { DeviceAgent } = require('../device-agent')

const agent = new DeviceAgent({
  stateFile: path.join(os.homedir(), '.regent', 'device-agent.json'),
})
agent.loadState()

agent.startLocalServer()
  .then(() => {
    console.log('Regent 设备代理已启动：http://127.0.0.1:12601/status')
    console.log('在浏览器打开 Regent 设置页完成自动配对。')
  })
  .catch((error) => {
    console.error(`设备代理启动失败：${error.message}`)
    process.exitCode = 1
  })

process.on('SIGTERM', () => {
  agent.stopLocalServer()
  process.exit(0)
})
process.on('SIGINT', () => {
  agent.stopLocalServer()
  process.exit(0)
})
