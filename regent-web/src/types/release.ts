export type ReleaseAsset = {
  filename: string
  url: string
  sizeBytes: number
  sha256: string | null
}

export type ReleaseManifest = {
  version: string | null
  releaseCode: string | null
  releasedAt: string | null
  minShellVersion: string | null
  notes: string | null
  downloads: Record<string, ReleaseAsset>
}
