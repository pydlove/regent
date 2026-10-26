import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'com.pydlove.regent.mobile',
  appName: 'Regent',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
    iosScheme: 'capacitor',
  },
  plugins: {
    CapacitorUpdater: {
      autoUpdate: false,
      appReadyTimeout: 15_000,
      responseTimeout: 60,
      autoDeleteFailed: true,
      autoDeletePrevious: false,
      resetWhenUpdate: true,
      statsUrl: '',
    },
  },
}

export default config
