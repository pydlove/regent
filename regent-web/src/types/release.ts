export type ReleaseAsset = {
  filename: string
  url: string
  sizeBytes: number
  sha256: string | null
}

export type ReleaseRuntime = ReleaseAsset & {
  version: string
  releaseCode: string
  minShellRelease: string
  signature: string
}

export type ReleaseManifest = {
  version: string | null
  releaseCode: string | null
  clientReleaseCode: string | null
  webOnly: boolean
  releasedAt: string | null
  minShellVersion: string | null
  notes: string | null
  runtime: ReleaseRuntime | null
  downloads: Record<string, ReleaseAsset>
}
