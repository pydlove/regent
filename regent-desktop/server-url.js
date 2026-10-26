const DEFAULT_SERVER_URL = 'https://ichuang.top/regent'
const DEFAULT_LANDING_PATH = '/decisions'

function normalizeServerUrl(value) {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error('请输入 Regent 服务地址')
  }
  let url
  try {
    url = new URL(value.trim())
  } catch {
    throw new Error('服务地址格式不正确')
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('服务地址必须以 http:// 或 https:// 开头')
  }
  if (url.username || url.password) {
    throw new Error('服务地址不能包含用户名或密码')
  }
  const pathname = url.pathname.replace(/\/+$/, '')
  return `${url.origin}${pathname}`
}

function appendServerPath(serverUrl, pathname) {
  if (/^https?:\/\//i.test(pathname)) return new URL(pathname).toString()
  return new URL(
    String(pathname || '').replace(/^\/+/, ''),
    `${serverUrl.replace(/\/+$/, '')}/`,
  ).toString()
}

module.exports = {
  DEFAULT_LANDING_PATH,
  DEFAULT_SERVER_URL,
  appendServerPath,
  normalizeServerUrl,
}
