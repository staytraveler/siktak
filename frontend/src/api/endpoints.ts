import { apiClient } from './client'
import type {
  AdminTable,
  ApplicationItem,
  ChatMessageItem,
  DemoStatus,
  FeedbackItem,
  MatchRun,
  MyTable,
  Region,
  Resolution,
  ResolutionChoice,
  Slot,
} from './types'
import type { Session, SessionUser } from './session'

const get = async <T,>(url: string) => (await apiClient.get<T>(url)).data
const post = async <T,>(url: string, body?: unknown) => (await apiClient.post<T>(url, body)).data

export const api = {
  // 인증
  login: (loginId: string, password: string) => post<Session>('/api/v1/auth/login', { loginId, password }),
  me: () => get<SessionUser>('/api/v1/auth/me'),

  regions: () => get<Region[]>('/api/v1/regions'),

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
  myFeedbacks: () => get<FeedbackItem[]>('/api/v1/me/feedbacks'),
  submitFeedback: (body: {
    applicationId: number
    tableRating: number
    conversationRating: number
    venueRating: number
    comment?: string
  }) => post<FeedbackItem>('/api/v1/feedbacks', body),

  // 운영자
  adminSlots: () => get<Slot[]>('/api/v1/admin/slots'),
  createSlot: (body: {
    regionId: number
    startsAt: string
    endsAt: string
    capacity: number
    applyDeadlineAt: string
    matchRunAt: string
  }) => post<Slot>('/api/v1/admin/slots', body),
  runMatching: (slotId: number) => post<MatchRun>(`/api/v1/admin/slots/${slotId}/run-matching`),
  slotTables: (slotId: number) => get<AdminTable[]>(`/api/v1/admin/slots/${slotId}/tables`),

  // 데모 도구
  demoStatus: () => get<DemoStatus>('/api/v1/admin/demo'),
  demoReset: () => post<DemoStatus>('/api/v1/admin/demo/reset'),
  demoJump: (code: string) => post<DemoStatus>(`/api/v1/admin/demo/jump/${code}`),
  demoAdvance: (minutes: number) => post<DemoStatus>(`/api/v1/admin/demo/advance?minutes=${minutes}`),
}
