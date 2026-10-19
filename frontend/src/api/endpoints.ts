import { apiClient } from './client'
import type {
  AdminTableDetail,
  Dashboard,
  ExceptionCase,
  MatchRunDetail,
  MatchRunItem,
  TableMessage,
  TableHistoryEntry,
  TableNotification,
  AdminReport,
  AdminTable,
  ApplicationItem,
  Attendance,
  ChatMessageItem,
  Companion,
  DemoActionResult,
  DemoStatus,
  FeedbackItem,
  HistoryItem,
  MatchRun,
  MyTable,
  NotificationItem,
  PreferenceChoice,
  Profile,
  ProfileInput,
  Recommendation,
  Region,
  ReportReason,
  Resolution,
  ResolutionChoice,
  ResolveAction,
  Slot,
  SlotInput,
  ApplicantRow,
  FeedbackSummary,
  MatchingSettings,
  Venue,
  VenueCapacity,
  VenueInput,
} from './types'
import type { Session, SessionUser } from './session'

const get = async <T,>(url: string) => (await apiClient.get<T>(url)).data
const post = async <T,>(url: string, body?: unknown) => (await apiClient.post<T>(url, body)).data

export const api = {
  // 인증
  login: (loginId: string, password: string) => post<Session>('/api/v1/auth/login', { loginId, password }),
  me: () => get<SessionUser>('/api/v1/auth/me'),

  regions: () => get<Region[]>('/api/v1/regions'),
  myProfile: () => get<Profile>('/api/v1/me/profile'),
  saveProfile: async (body: ProfileInput) => (await apiClient.put<Profile>('/api/v1/me/profile', body)).data,

  // 참가자
  openSlots: () => get<Slot[]>('/api/v1/slots'),
  myApplications: () => get<ApplicationItem[]>('/api/v1/me/applications'),
  submitApplication: (slotIds: number[]) =>
    post<ApplicationItem>('/api/v1/applications', { slotIds, passType: 'SINGLE' }),
  mockApprove: (applicationId: number) =>
    post<ApplicationItem>(`/api/v1/applications/${applicationId}/payments/mock-approve`),
  resolution: (applicationId: number) => get<Resolution>(`/api/v1/me/applications/${applicationId}/resolution`),
  chooseResolution: (applicationId: number, choice: ResolutionChoice, slotId?: number) =>
    post<Resolution>(`/api/v1/me/applications/${applicationId}/resolution`, { choice, slotId }),
  myTable: (applicationId: number) => get<MyTable>(`/api/v1/me/applications/${applicationId}/table`),
  chatMessages: (chatRoomId: number) => get<ChatMessageItem[]>(`/api/v1/chat-rooms/${chatRoomId}/messages`),
  sendTableMessage: (tableId: number, content: string) =>
    post<ChatMessageItem>(`/api/v1/tables/${tableId}/messages`, { content }),
  myNotifications: () => get<NotificationItem[]>('/api/v1/me/notifications'),
  readNotification: (id: number) => post<NotificationItem>(`/api/v1/me/notifications/${id}/read`),
  myFeedbacks: () => get<FeedbackItem[]>('/api/v1/me/feedbacks'),
  submitFeedback: (body: {
    applicationId: number
    tableRating: number
    conversationRating: number
    venueRating: number
    rejoinIntent: number
    recommendScore: number
    comment?: string
  }) => post<FeedbackItem>('/api/v1/feedbacks', body),
  attendance: (applicationId: number) => get<Attendance>(`/api/v1/me/applications/${applicationId}/attendance`),
  checkIn: (applicationId: number) => post<Attendance>(`/api/v1/me/applications/${applicationId}/check-in`),
  companions: (applicationId: number) => get<Companion[]>(`/api/v1/me/applications/${applicationId}/companions`),
  personPreferences: (applicationId: number) =>
    get<PreferenceChoice[]>(`/api/v1/me/applications/${applicationId}/person-preferences`),
  savePersonPreferences: async (applicationId: number, choices: PreferenceChoice[]) =>
    (await apiClient.put<PreferenceChoice[]>(`/api/v1/me/applications/${applicationId}/person-preferences`, { choices }))
      .data,
  blockCompanion: (applicationId: number, companionId: number) =>
    post<void>('/api/v1/me/blocks', { applicationId, companionId }),
  reportCompanion: (body: {
    applicationId: number
    companionId: number
    reasonType: ReportReason
    description: string
  }) => post<{ reportId: number; reportStatus: string }>('/api/v1/me/reports', body),
  myHistory: () => get<HistoryItem[]>('/api/v1/me/history'),
  myRecommendations: () => get<Recommendation[]>('/api/v1/me/recommendations'),

  // 운영자
  adminReports: (open: boolean) => get<AdminReport[]>(`/api/v1/admin/reports?open=${open}`),
  resolveReport: (reportId: number, body: { action: ResolveAction; days?: number; note?: string }) =>
    post<AdminReport>(`/api/v1/admin/reports/${reportId}/resolve`, body),
  adminSlots: () => get<Slot[]>('/api/v1/admin/slots'),
  createSlot: (body: {
    regionId: number
    startsAt: string
    endsAt: string
    capacity: number
    applyDeadlineAt: string
    matchRunAt: string
  }) => post<Slot>('/api/v1/admin/slots', body),
  updateSlot: async (id: number, body: SlotInput) => (await apiClient.put<Slot>(`/api/v1/admin/slots/${id}`, body)).data,
  adminVenues: () => get<Venue[]>('/api/v1/admin/venues'),
  createVenue: (body: VenueInput) => post<Venue>('/api/v1/admin/venues', body),
  updateVenue: async (id: number, body: VenueInput) => (await apiClient.put<Venue>(`/api/v1/admin/venues/${id}`, body)).data,
  venueCapacities: (slotId: number) => get<VenueCapacity[]>(`/api/v1/admin/venue-capacities?slotId=${slotId}`),
  setVenueCapacity: async (venueId: number, slotId: number, maxTables: number) =>
    (await apiClient.put<VenueCapacity>(`/api/v1/admin/venues/${venueId}/capacities/${slotId}`, { maxTables })).data,
  matchingSettings: () => get<MatchingSettings>('/api/v1/admin/settings/matching'),
  saveMatchingSettings: async (body: MatchingSettings) =>
    (await apiClient.put<MatchingSettings>('/api/v1/admin/settings/matching', body)).data,
  applicants: (slotId?: number) => get<ApplicantRow[]>(`/api/v1/admin/applicants${slotQuery(slotId)}`),
  feedbackSummary: (slotId?: number) => get<FeedbackSummary>(`/api/v1/admin/feedback-summary${slotQuery(slotId)}`),
  runMatching: (slotId: number) => post<MatchRun>(`/api/v1/admin/slots/${slotId}/run-matching`),
  slotTables: (slotId: number) => get<AdminTable[]>(`/api/v1/admin/slots/${slotId}/tables`),

  // 운영자 어드민 (8.1)
  dashboard: () => get<Dashboard>('/api/v1/admin/dashboard'),
  exceptions: () => get<ExceptionCase[]>('/api/v1/admin/exceptions'),
  exceptionAction: (id: number, action: string, reason?: string) =>
    post<{ caseId: number; caseStatus: string }>(`/api/v1/admin/exceptions/${id}/${action}`, reason === undefined ? undefined : { reason }),
  matchRuns: () => get<MatchRunItem[]>('/api/v1/admin/match-runs'),
  matchRun: (id: number) => get<MatchRunDetail>(`/api/v1/admin/match-runs/${id}`),
  adminTable: (id: number) => get<AdminTableDetail>(`/api/v1/admin/tables/${id}`),
  tableMessages: (id: number) => get<TableMessage[]>(`/api/v1/admin/tables/${id}/messages`),
  tableHistory: (id: number) => get<TableHistoryEntry[]>(`/api/v1/admin/tables/${id}/history`),
  tableNotifications:(id: number) => get<TableNotification[]>(`/api/v1/admin/tables/${id}/notifications`),

  // 데모 도구
  demoStatus: () => get<DemoStatus>('/api/v1/admin/demo'),
  demoReset: () => post<DemoStatus>('/api/v1/admin/demo/reset'),
  demoJump: (code: string) => post<DemoStatus>(`/api/v1/admin/demo/jump/${code}`),
  demoAdvance: (minutes: number) => post<DemoStatus>(`/api/v1/admin/demo/advance?minutes=${minutes}`),
  demoCheckInAll: () => post<DemoActionResult>('/api/v1/admin/demo/check-in-all'),
  demoFeedbackAll: () => post<DemoActionResult>('/api/v1/admin/demo/feedback-all'),
  demoRound2: () => post<DemoActionResult>('/api/v1/admin/demo/round2-applications'),
}

const slotQuery = (slotId?: number) => (slotId ? `?slotId=${slotId}` : '')

// CSV는 Authorization 헤더가 필요해서 링크 대신 blob으로 받아 저장한다 (PRD 16장).
export async function downloadCsv(kind: 'applicants' | 'tables', slotId?: number) {
  const res = await apiClient.get<Blob>(`/api/v1/admin/exports/${kind}.csv${slotQuery(slotId)}`, { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = `${kind}${slotId ? `-slot${slotId}` : ''}.csv`
  a.click()
  URL.revokeObjectURL(url)
}
