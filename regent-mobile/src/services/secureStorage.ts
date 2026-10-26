import { Capacitor } from '@capacitor/core'
import { KeychainAccess, SecureStorage } from '@aparajita/capacitor-secure-storage'

export const SESSION_KEY = 'regent.mobile.session'
export const INSTALLATION_KEY = 'regent.mobile.installation'
export const CACHE_PREFIX = 'regent.mobile.cache.'

let secureStorageReady: Promise<void> | null = null

function prepareSecureStorage(): Promise<void> {
  if (!Capacitor.isNativePlatform()) return Promise.resolve()
  if (!secureStorageReady) {
    secureStorageReady = (async () => {
      await SecureStorage.setKeyPrefix('regent-mobile_')
      await SecureStorage.setSynchronize(false)
      await SecureStorage.setDefaultKeychainAccess(
        KeychainAccess.whenUnlockedThisDeviceOnly,
      )
    })()
  }
  return secureStorageReady
}

export async function getSecureItem(key: string): Promise<string | null> {
  if (Capacitor.isNativePlatform()) {
    await prepareSecureStorage()
    return SecureStorage.getItem(key)
  }
  return globalThis.sessionStorage?.getItem(key) ?? null
}

export async function setSecureItem(key: string, value: string): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    await prepareSecureStorage()
    await SecureStorage.setItem(key, value)
    return
  }
  globalThis.sessionStorage?.setItem(key, value)
}

export async function removeSecureItem(key: string): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    await prepareSecureStorage()
    await SecureStorage.removeItem(key)
    return
  }
  globalThis.sessionStorage?.removeItem(key)
}

export function readInstallationId(): string | null {
  try {
    return globalThis.localStorage?.getItem(INSTALLATION_KEY) ?? null
  } catch {
    return null
  }
}

export function saveInstallationId(value: string): void {
  globalThis.localStorage?.setItem(INSTALLATION_KEY, value)
}

export function clearAccountCaches(): void {
  try {
    const storage = globalThis.localStorage
    if (!storage) return
    for (let index = storage.length - 1; index >= 0; index -= 1) {
      const key = storage.key(index)
      if (key?.startsWith(CACHE_PREFIX)) storage.removeItem(key)
    }
  } catch {
    // 存储不可用时没有残留可清，内存缓存仍由调用方显式清除。
  }
}
