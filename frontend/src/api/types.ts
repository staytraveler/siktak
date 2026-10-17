// API 응답 타입. OpenAPI 타입 생성(src/api/generated/) 도입 전까지 수동 정의한다.

export interface Region {
  id: number
  name: string
}

export interface Slot {
  id: number
  regionId: number
  regionName: string
  startsAt: string
  endsAt: string
  capacity: number
  applyDeadlineAt: string
  matchRunAt: string
  slotStatus: string
}

export interface ApplicationItem {
  id: number
  passType: string
  applicationStatus: string
  paymentStatus: string | null
  matchStatus: string | null
  assignedSlotId: number | null
  paymentDueAt: string | null
  submittedAt: string | null
  matchFailReason: string | null
  slotChoices: { priority: number; slot: Slot | null }[]
}

export interface MyTable {
  tableId: number
  slotId: number
  memberCount: number
  confirmedAt: string
  chatRoomId: number | null
  reasons: string[]
}

export interface ChatMessageItem {
  id: number
  senderType: 'USER' | 'SYSTEM'
  senderId: number | null
  messageType: string
  body: string
  createdAt: string
}

export interface FeedbackItem {
  id: number
  applicationId: number
  tableRating: number
  conversationRating: number
  venueRating: number
  comment: string | null
  submittedAt: string
}

export interface MatchRun {
  id: number
  runKey: string
  algorithmVersion: string
  runStatus: string
  candidateCount: number
  summary: Record<string, unknown>
  startedAt: string
  finishedAt: string | null
}

export interface AdminTable {
  id: number
  tableStatus: string
  memberCount: number
  confirmedAt: string
  chatRoomId: number | null
  summary: Record<string, unknown>
  members: { applicationId: number; userId: number; name: string | null }[]
}

export interface DemoStatus {
  now: string
  offsetSeconds: number
  t0: string | null
  seedVersion: string | null
  jumps: { code: string; label: string; at: string; reached: boolean }[]
  upcomingJobs: { jobType: string; targetId: number; dueAt: string }[]
}

// 매칭 실패 후 선택 (6.1, GET/POST /api/v1/me/applications/{id}/resolution)
export type ResolutionChoice = 'TRANSFER' | 'KEEP' | 'REFUND'

export interface Resolution {
  applicationId: number
  matchStatus: string | null
  paymentStatus: string | null
  matchFailReason: string | null
  resolutionStatus: 'OFFERED' | 'RESOLVED' | 'AUTO_KEPT'
  respondBy: string
  choice: ResolutionChoice | null
  chosenSlotId: number | null
  newApplicationId: number | null
  respondedAt: string | null
  offeredSlots: { slot: Slot; reasonCode: string; reason: string; available: boolean }[]
  choices: { choice: ResolutionChoice; available: boolean; description: string }[]
}
