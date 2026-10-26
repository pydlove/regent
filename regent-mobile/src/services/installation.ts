import { Capacitor } from '@capacitor/core'

import {
  INSTALLATION_KEY,
  readInstallationId,
  saveInstallationId,
} from './secureStorage'

export function getOrCreateInstallationId(): string {
  const stored = readInstallationId()
  if (stored && /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/.test(stored)) {
    return stored
  }
  const generated = `${Capacitor.getPlatform()}-${crypto.randomUUID()}`
  saveInstallationId(generated)
  return generated
}

export function describeInstallationId(value: string): string {
  return value.length <= 18 ? value : `${value.slice(0, 10)}…${value.slice(-6)}`
}

export { INSTALLATION_KEY }
