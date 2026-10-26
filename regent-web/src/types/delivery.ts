export type InitiativeStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export type ProductVersionStatus = 'PLANNING' | 'CONFIRMED' | 'CLOSED'

export type ProductVersion = {
  id: number
  projectId: number
  versionNo: string
  description: string | null
  status: ProductVersionStatus
  createdBy: number
  createdAt: string
  updatedAt: string
}

export type Initiative = {
  id: number
  projectId: number
  versionId: number | null
  projectName: string
  title: string
  businessGoal: string
  scopeText: string | null
  outOfScopeText: string | null
  successMetrics: string | null
  stakeholders: string | null
  targetVersion: string | null
  riskText: string | null
  meetingMinutes: string | null
  status: InitiativeStatus
  submittedAt: string | null
  approvedAt: string | null
  rejectedAt: string | null
  createdAt: string
  updatedAt: string
}

export type InitiativeAttachment = {
  id: number
  initiativeId: number
  filename: string
  contentType: string
  sizeBytes: number
  contentUrl: string
  uploadedBy: number
  createdAt: string
}

export type DeliveryStage =
  | 'REQUIREMENT_REVIEW'
  | 'DESIGN'
  | 'DESIGN_REVIEW'
  | 'DEVELOPMENT'
  | 'SMOKE'
  | 'AUTOMATION'
  | 'READY_FOR_TRANSFER'
  | 'IN_RELEASE'
  | 'RELEASED'
  | 'CLOSED'

export type DeliveryStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED'

export type DeliveryGate = {
  id: number
  scope: 'INITIATIVE' | 'REQUIREMENT_DELIVERY' | 'RELEASE'
  scopeId: number
  type: string
  decision: 'PASSED' | 'REJECTED'
  roleCode: string
  comment: string | null
  decidedBy: number
  decidedAt: string
}

export type RequirementDelivery = {
  id: number
  initiativeId: number
  projectId: number
  requirementId: number
  requirementTitle: string
  initiativeTitle: string
  projectName: string
  stage: DeliveryStage
  status: DeliveryStatus
  designSummary: string | null
  designContent: string | null
  createdAt: string
  updatedAt: string
  gates: DeliveryGate[]
}

export type BuildArtifact = {
  id: number
  version: string
  gitCommit: string
  digest: string | null
  sizeBytes: number
  storageRef: string | null
  builtAt: string
}

export type ReleaseStage =
  | 'TRANSFER'
  | 'INTEGRATION'
  | 'REGRESSION'
  | 'RELEASE_REVIEW'
  | 'PRODUCTION'
  | 'OBSERVING'
  | 'CLOSED'

export type ReleaseStatus = 'ACTIVE' | 'RELEASED' | 'CANCELLED' | 'SUPERSEDED'

export type Release = {
  id: number
  projectId: number
  projectName: string
  version: string
  attemptNo: number
  stage: ReleaseStage
  status: ReleaseStatus
  releaseNotes: string | null
  rollbackPlan: string
  createdAt: string
  updatedAt: string
  artifact: BuildArtifact
  deliveries: Array<{
    id: number
    requirementId: number
    requirementTitle: string
    stage: DeliveryStage
    status: DeliveryStatus
  }>
  gates: DeliveryGate[]
}

export type InitiativeInput = {
  projectId: number
  versionId?: number | null
  title: string
  businessGoal: string
  scopeText: string
  outOfScopeText: string
  successMetrics: string
  stakeholders: string
  targetVersion: string
  riskText: string
  meetingMinutes?: string | null
}

export type RequirementDeliveryAction =
  | 'APPROVE_REQUIREMENT_REVIEW'
  | 'REJECT_REQUIREMENT_REVIEW'
  | 'SUBMIT_DESIGN'
  | 'APPROVE_DESIGN'
  | 'REJECT_DESIGN'
  | 'COMPLETE_DEVELOPMENT'
  | 'PASS_SMOKE'
  | 'PASS_AUTOMATION'
  | 'CLOSE'
  | 'CANCEL'

export type ReleaseAction =
  | 'APPROVE_TRANSFER'
  | 'PASS_INTEGRATION'
  | 'PASS_REGRESSION'
  | 'APPROVE_RELEASE'
  | 'MARK_DEPLOYED'
  | 'CLOSE'

export type ReleaseInput = {
  projectId: number
  version: string
  gitCommit: string
  digest: string
  sizeBytes: number
  storageRef: string
  releaseNotes: string
  rollbackPlan: string
  deliveryIds: number[]
}
