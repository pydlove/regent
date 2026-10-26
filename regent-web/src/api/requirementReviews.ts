import { apiResourceUrl, get, postForm, postJson } from '@/api/http'

export type RequirementReviewAttachment = {
  id: number
  filename: string
  contentType: string
  sizeBytes: number
  contentUrl: string
  uploadedBy: number
  createdAt: string
}

export type RequirementReviewRequirement = {
  requirementId: number
  requirementNo: number
  title: string
}

export type RequirementReviewRecord = {
  id: number
  versionId: number
  reviewNo: number
  reviewDate: string
  status: 'SUBMITTED' | 'APPROVED' | 'REJECTED'
  conclusion: string
  minutes: string | null
  reviewComment: string | null
  requirements: RequirementReviewRequirement[]
  submittedBy: number
  decidedBy: number | null
  decidedAt: string | null
  createdAt: string
  attachments: RequirementReviewAttachment[]
}

export type RecordRequirementReviewInput = {
  reviewDate: string
  minutes: string
  files: File[]
}

function resolveAttachment(
  attachment: RequirementReviewAttachment,
): RequirementReviewAttachment {
  return {
    ...attachment,
    contentUrl: apiResourceUrl(attachment.contentUrl),
  }
}

function resolveRecord(record: RequirementReviewRecord): RequirementReviewRecord {
  return {
    ...record,
    attachments: record.attachments.map(resolveAttachment),
  }
}

export const listRequirementReviewRecords = async (versionId: number) => {
  const records = await get<RequirementReviewRecord[]>(
    `/product-versions/${versionId}/requirement-review-records`,
  )
  return records.map(resolveRecord)
}

export const submitRequirementReview = async (
  versionId: number,
  input: RecordRequirementReviewInput,
) => {
  const form = new FormData()
  form.append('reviewDate', input.reviewDate)
  form.append('minutes', input.minutes)
  input.files.forEach((file) => form.append('files', file))
  return resolveRecord(
    await postForm<RequirementReviewRecord>(
      `/product-versions/${versionId}/requirement-review-records`,
      form,
    ),
  )
}

export const decideRequirementReview = (
  recordId: number,
  decision: 'APPROVE' | 'REJECT',
  comment: string,
) => postJson<RequirementReviewRecord>(
  `/requirement-review-records/${recordId}/decision`,
  { decision, comment },
)
