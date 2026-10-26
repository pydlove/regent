import { describe, expect, it } from 'vitest'

import { toMobileApiPath } from './apiPath'

describe('toMobileApiPath', () => {
  it('保留头像版本参数并去掉 API 前缀', () => {
    expect(toMobileApiPath('/api/profile/avatar?v=123')).toBe('/profile/avatar?v=123')
  })

  it('支持带部署子路径的绝对地址', () => {
    expect(
      toMobileApiPath('https://ichuang.top/regent/api/profile/avatar?v=456'),
    ).toBe('/profile/avatar?v=456')
  })
})
