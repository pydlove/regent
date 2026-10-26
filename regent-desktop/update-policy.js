const RELEASE_PATTERN = /^R(\d+)B(\d+)$/

function validReleaseCode(value) {
  return typeof value === 'string' && RELEASE_PATTERN.test(value)
}

/**
 * 返回本次发布真正要求的客户端 Runtime 版本。
 *
 * 纯网页发布只要求刷新页面，不应触发桌面客户端升级；旧版客户端则通过顶层
 * releaseCode 保持与当前 Runtime 一致，避免升级提示。
 */
function clientUpdateReleaseCode(manifest) {
  if (!manifest || manifest.webOnly === true) return ''
  const runtimeRelease = manifest.runtime?.releaseCode
  if (validReleaseCode(runtimeRelease)) return runtimeRelease
  return validReleaseCode(manifest.releaseCode) ? manifest.releaseCode : ''
}

function shouldPromptForClientUpdate(
  manifest,
  activeRelease,
  promptedRelease = null,
  failedRelease = null,
  skippedRelease = null,
) {
  const targetRelease = clientUpdateReleaseCode(manifest)
  return Boolean(
    targetRelease
    && targetRelease !== activeRelease
    && targetRelease !== promptedRelease
    && targetRelease !== failedRelease
    && targetRelease !== skippedRelease,
  )
}

module.exports = {
  clientUpdateReleaseCode,
  shouldPromptForClientUpdate,
  validReleaseCode,
}
