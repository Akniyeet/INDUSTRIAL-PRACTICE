import type { ApiClient } from '../client'

export interface UploadSlotResponse {
  assetId: string
  uploadUrl: string
  maxSizeBytes: number
  uploadWindowSeconds: number
  expiresAt: string
}

export interface FileAssetResponse {
  id: string
  purpose: string
  state: string
  contentType: string
  sizeBytes: number
}

export interface DownloadUrlResponse {
  assetId: string
  url: string
  ttlSeconds: number
}

/**
 * Two-step upload flow via MinIO presigned URLs.
 *
 * 1. createSlot() → get presigned PUT URL
 * 2. Client uploads directly to MinIO
 * 3. confirm() → backend validates and returns metadata
 */
export class StorageApi {
  constructor(private readonly client: ApiClient) {}

  /** Step 1: Reserve an upload slot and get a presigned PUT URL. */
  createSlot(purpose: 'COVER' | 'CTA_FILE' | 'RECORDING' | 'INVOICE', contentType: string) {
    return this.client.post<UploadSlotResponse>('/v1/storage/uploads', {
      purpose,
      contentType,
    })
  }

  /** Step 3: Confirm the upload after PUT completes. */
  confirm(assetId: string) {
    return this.client.post<FileAssetResponse>(`/v1/storage/uploads/${assetId}/confirm`)
  }

  /** Get a presigned download URL for a confirmed asset. */
  downloadUrl(assetId: string) {
    return this.client.get<DownloadUrlResponse>(`/v1/storage/${assetId}/download-url`)
  }

  /** Delete an asset. */
  remove(assetId: string) {
    return this.client.delete(`/v1/storage/${assetId}`)
  }
}
