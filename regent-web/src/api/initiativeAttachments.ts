import { apiResourceUrl, get, postForm } from '@/api/http'
import type { InitiativeAttachment } from '@/types/delivery'

function resolveAttachment(attachment: InitiativeAttachment): InitiativeAttachment {
  return {
    ...attachment,
    contentUrl: apiResourceUrl(attachment.contentUrl),
  }
}

export const listInitiativeAttachments = async (initiativeId: number) => {
  const items = await get<InitiativeAttachment[]>(
    `/initiatives/${initiativeId}/attachments`,
  )
  return items.map(resolveAttachment)
}

export const uploadInitiativeAttachment = async (
  initiativeId: number,
  file: File,
) => {
  const form = new FormData()
  form.append('file', file)
  return resolveAttachment(
    await postForm<InitiativeAttachment>(`/initiatives/${initiativeId}/attachments`, form),
  )
}
