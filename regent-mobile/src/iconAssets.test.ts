import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const desktopRoot = path.resolve(mobileRoot, '../regent-desktop')

describe('Regent 图标资源', () => {
  it('桌面端和移动端使用同一份图标源文件', () => {
    const desktop = fs.readFileSync(path.join(desktopRoot, 'assets/icon.svg'), 'utf8')
    const mobile = fs.readFileSync(path.join(mobileRoot, 'assets/icon.svg'), 'utf8')
    expect(mobile).toBe(desktop)
  })

  it('Android 自适应图标使用与桌面端一致的深色背景', () => {
    const background = fs.readFileSync(
      path.join(mobileRoot, 'android/app/src/main/res/values/ic_launcher_background.xml'),
      'utf8',
    )
    expect(background).toContain('#171719')
  })
})
