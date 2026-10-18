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

// ---- 8.2 운영자 관리
export interface SlotInput {
  regionId: number
  startsAt: string
  endsAt: string
  capacity: number
  applyDeadlineAt: string
  matchRunAt: string
}

export interface Venue {
  id: number
  regionId: number
  name: string
  address: string
  mapUrl: string | null
  priceRange: string | null
  description: string | null
  active: boolean
}

export type VenueInput = Omit<Venue, 'id' | 'active'> & { active?: boolean }

export interface VenueCapacity {
  venueId: number
  slotId: number
  maxTables: number
  reservedTables: number
  maxSeatsPerTable: number
}

export interface MatchingSettings {
  weights: Record<string, number>
  minScore: number
}

export interface ApplicantRow {
  applicationId: number
  userId: number
  name: string
  loginId: string
  applicationStatus: string
  paymentStatus: string | null
  matchStatus: string | null
  attendanceStatus: string | null
  assignedSlotId: number | null
  slotChoices: string | null
  submittedAt: string | null
  birthYear: number | null
  gender: string | null
  job: string | null
  mbti: string | null
  attendedCount: number
  reportedCount: number
}

export interface FeedbackAverages {
  count: number
  table: number | null
  conversation: number | null
  venue: number | null
  rejoin: number | null
  recommend: number | null
}

export interface FeedbackSummary {
  overall: FeedbackAverages
  wantAgainCount: number
  avoidCount: number
  tables: { tableId: number; slotId: number; averages: FeedbackAverages; wantAgainCount: number; avoidCount: number }[]
  comments: { tableId: number; slotId: number; tableRating: number; comment: string; submittedAt: string }[]
}

// ---- 운영자 어드민 (8.1) ----

export interface Rate {
  numerator: number
  denominator: number
  rate: number | null
}

export type CaseType =
  | 'PAYMENT_ERROR'
  | 'REFUND_FAILED'
  | 'DATA_MISSING'
  | 'MATCH_CONFLICT'
  | 'VENUE_UNAVAILABLE'
  | 'NOTIFICATION_FAILED'
  | 'SAFETY_REPORT'
  | 'NO_SHOW_CANDIDATE'

export interface Dashboard {
  totals: {
    applicants: number
    applications: number
    paid: number
    confirmed: number
    waitingChoice: number
    refunded: number
  }
  slots: {
    slotId: number
    regionName: string
    startsAt: string
    slotStatus: string
    applied: number
    paid: number
    confirmed: number
    waiting: number
    refunds: number
  }[]
  metrics: {
    autoConfirm: Rate
    autoRealloc: Rate
    autoRefund: Rate
    operatorIntervention: Rate
    exceptionRate: Rate
    matchSuccess: Rate
  }
  exceptions: { total: number; byType: Record<CaseType, number> }
}

export interface ExceptionCase {
  id: number
  caseType: CaseType
  caseStatus: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED'
  applicationId: number | null
  diningTableId: number | null
  reason: string
  detail: Record<string, unknown> | null
  caseKey: string
  createdAt: string
  resolvedBy: number | null
  resolutionNote: string | null
  resolvedAt: string | null
}

export interface PlacementLog {
  id: number
  applicationId: number
  decision: string
  fromSlotId: number | null
  toSlotId: number | null
  toTableId: number | null
  reallocStep: number | null
  reasonCode: string
  causeCode: string | null
  reasonText: string
  loginId: string | null
  userName: string | null
}

export interface MatchRunItem {
  id: number
  runKey: string
  algorithmVersion: string
  runStatus: string
  candidateCount: number
  params: Record<string, unknown>
  summary: Record<string, unknown>
  startedAt: string
  finishedAt: string | null
}

export interface MatchRunDetail {
  run: MatchRunItem
  tables: { id: number; slotId: number; tableStatus: string; memberCount: number; venueAssigned: boolean }[]
  placementLogs: PlacementLog[]
}

export interface AdminTableDetail {
  id: number
  slotId: number
  matchRunId: number | null
  tableStatus: string
  memberCount: number
  totalScore: number | null
  scoreDetail: Record<string, unknown>
  summary: Record<string, unknown>
  venue: { id: number; name: string; address: string; priceRange: string | null } | null
  reservationName: string | null
  venueAssignedAt: string | null
  confirmedAt: string | null
  chatRoomId: number | null
  members: {
    applicationId: number
    userId: number
    name: string | null
    loginId: string | null
    birthYear: number | null
    gender: string | null
    mbti: string | null
    interests: string[]
  }[]
}

export interface TableMessage {
  id: number
  senderType: string
  messageType: string
  body: string
  createdAt: string
}

export interface TableNotification {
  id: number
  userId: number
  userName: string | null
  templateCode: string
  sendStatus: 'PENDING' | 'SENT' | 'FAILED' | 'CANCELED'
  attempts: number
  lastError: string | null
  scheduledAt: string
  sentAt: string | null
}
