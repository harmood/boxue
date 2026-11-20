import { apiFetch, apiFetchBlob } from './http'

export interface MediaView {
  id: string
  sha256: string
  mimeType: string
  sizeBytes: number
  width: number | null
  height: number | null
  url: string
  deduped: boolean
  createdAt: string
}

export function uploadMedia(file: File) {
  const body = new FormData()
  body.append('file', file)
  return apiFetch<MediaView>('/media', { method: 'POST', body })
}

export function getMedia(mediaId: string) {
  return apiFetch<MediaView>(`/media/${mediaId}`)
}

export function deleteMedia(mediaId: string) {
  return apiFetch<void>(`/media/${mediaId}`, { method: 'DELETE' })
}

const objectUrlCache = new Map<string, Promise<string>>()

export function getMediaObjectUrl(mediaId: string): Promise<string> {
  const cached = objectUrlCache.get(mediaId)
  if (cached) {
    return cached
  }
  const promise = apiFetchBlob(`/media/${mediaId}/content`)
    .then((blob) => URL.createObjectURL(blob))
    .catch((error) => {
      objectUrlCache.delete(mediaId)
      throw error
    })
  objectUrlCache.set(mediaId, promise)
  return promise
}
