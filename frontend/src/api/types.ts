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
  remainingSeats?: number | null
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
  payload: Record<string, unknown> | null
  createdAt: string
}

export interface FeedbackItem {
  id: number
  applicationId: number
  tableRating: number
  conversationRating: number
  venueRating: number
  rejoinIntent: number | null
  recommendScore: number | null
  comment: string | null
  submittedAt: string
}

// 체크인 (7.1, GET/POST /api/v1/me/applications/{id}/attendance|check-in)
export interface Attendance {
  applicationId: number
  attendanceStatus: 'SCHEDULED' | 'ATTENDED' | 'CANCELED_EARLY' | 'CANCELED_LATE' | 'NO_SHOW'
  checkedInAt: string | null
  checkInOpensAt: string
  checkInClosesAt: string
  canCheckIn: boolean
}

// 사람별 선호 (비공개, 동석자는 이름만)
export type PreferenceType = 'WANT_AGAIN' | 'AVOID'
export interface Companion {
  companionId: number
  name: string
}
export interface PreferenceChoice {
  companionId: number
  preference: PreferenceType
}

export interface DemoActionResult {
  action: string
  done: number
  skipped: number
  message: string
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

export interface Profile {
  complete: boolean
  birthYear: number | null
  gender: string | null
  job: string | null
  mbti: string | null
  interests: string[]
  selfTraits: string[]
  preferredTraits: string[]
  conversationStyle: string | null
  priceRange: string | null
  foodRestrictions: string | null
  allergies: string | null
  privacyAgreedAt: string | null
  policyAgreedAt: string | null
}

export type ProfileInput = Omit<Profile, 'complete' | 'privacyAgreedAt' | 'policyAgreedAt'> & {
  privacyAgreed: boolean
  policyAgreed: boolean
}

export interface NotificationItem {
  id: number
  templateCode: string
  title: string
  body: string
  payload: Record<string, unknown> | null
  sentAt: string | null
  readAt: string | null
}

// 신고·차단 (SAF-01)
export type ReportReason =
  | 'HARASSMENT'
  | 'INAPPROPRIATE_BEHAVIOR'
  | 'PRIVACY_VIOLATION'
  | 'SAFETY_THREAT'
  | 'OTHER'

// 참가 이력·추천 (RET-01·02)
export interface HistoryItem {
  applicationId: number
  eventSlotId: number
  startsAt: string
  regionName: string
  venueName: string | null
  attendanceStatus: string
  feedbackSubmitted: boolean
}
export interface Recommendation {
  eventSlotId: number
  startsAt: string
  regionId: number
  regionName: string
  sameRegion: boolean
  sameTimeSlot: boolean
}

// 운영자 신고 조치 (SAF-02)
export type ResolveAction = 'DISMISS' | 'WARNING' | 'RESTRICTION' | 'PERMANENT_BAN'
export interface AdminReport {
  id: number
  reporterId: number
  reporterName: string
  reportedId: number
  reportedName: string
  diningTableId: number | null
  reasonType: string
  description: string
  reportStatus: string
  handledBy: number | null
  handledAt: string | null
  createdAt: string
}
