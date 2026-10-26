const RELEASE_PATTERN = /^Regent_V(\d+(?:\.\d+){2})_(R\d+)B(\d+)$/

export function compareReleaseVersion(left: string, right: string): number {
  const a = left.match(RELEASE_PATTERN)
  const b = right.match(RELEASE_PATTERN)
  if (!a || !b) return left === right ? 0 : 0
  const product = (value: string) =>
    value.split('.').reduce((result, part) => result * 1000 + Number(part), 0)
  const productDiff = product(a[1]) - product(b[1])
  if (productDiff !== 0) return Math.sign(productDiff)
  const releaseDiff = Number(a[2].slice(1)) - Number(b[2].slice(1))
  if (releaseDiff !== 0) return Math.sign(releaseDiff)
  return Math.sign(Number(a[3]) - Number(b[3]))
}

export function isNewerRelease(candidate: string, current: string): boolean {
  return compareReleaseVersion(candidate, current) > 0
}

/**
 * 版本记录里的紧凑展示：保留产品版本和完整客户端发布码。
 *
 * 不能只显示 B 序号。R 段表示发布代际，只截 B 会让 R100B013 看起来像另一个版本。
 */
export function shortReleaseVersion(version: string): string {
  const match = String(version || '').match(RELEASE_PATTERN)
  if (!match) return version
  return `V${match[1]} · ${match[2]}B${match[3]}`
}
