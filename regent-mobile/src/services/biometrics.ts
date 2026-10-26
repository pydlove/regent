import {
  BiometricAuth,
  BiometryError,
  BiometryErrorType,
  BiometryType,
  type CheckBiometryResult,
} from '@aparajita/capacitor-biometric-auth'
import { Capacitor } from '@capacitor/core'

export type BiometryState = CheckBiometryResult & {
  label: string
  native: boolean
}

function labelFor(type: BiometryType): string {
  switch (type) {
    case BiometryType.faceId:
      return '面容 ID'
    case BiometryType.touchId:
      return '触控 ID'
    case BiometryType.fingerprintAuthentication:
      return '指纹'
    case BiometryType.faceAuthentication:
      return '人脸识别'
    case BiometryType.irisAuthentication:
      return '虹膜识别'
    default:
      return '系统密码'
  }
}

export async function getBiometryState(): Promise<BiometryState> {
  if (!Capacitor.isNativePlatform()) {
    return {
      isAvailable: false,
      strongBiometryIsAvailable: false,
      biometryType: BiometryType.none,
      biometryTypes: [],
      deviceIsSecure: false,
      reason: 'Web 调试模式',
      code: BiometryErrorType.none,
      label: 'Web 调试模式',
      native: false,
    }
  }
  const result = await BiometricAuth.checkBiometry()
  return {
    ...result,
    label: labelFor(result.biometryType),
    native: true,
  }
}

export async function authenticateDevice(reason: string): Promise<void> {
  if (!Capacitor.isNativePlatform()) return
  try {
    await BiometricAuth.authenticate({
      reason,
      cancelTitle: '取消',
      allowDeviceCredential: true,
      iosFallbackTitle: '使用系统密码',
      androidTitle: '解锁 Regent',
      androidSubtitle: reason,
      androidConfirmationRequired: false,
    })
  } catch (error) {
    if (error instanceof BiometryError) {
      throw new Error(error.code === BiometryErrorType.userCancel
        ? '已取消解锁'
        : error.message)
    }
    throw error
  }
}
