import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { ApplicationItem, PreferenceType, ResolutionChoice } from '../../api/types'
import StatusBadges from '../../components/StatusBadges'
import SessionBar from '../../components/SessionBar'
import { formatDateTime, PAYMENT_STATUS } from '../../lib/format'

// 신청 상세 (/app/applications/:id): 상태 → 결제 → 확정 테이블·단체방 → 피드백
export default function ApplicationDetailPage() {
  const id = Number(useParams().id)
  const applications = useQuery({ queryKey: ['my-applications'], queryFn: api.myApplications })
  const application = applications.data?.find((a) => a.id === id)

  return (
    <main className="mx-auto min-h-screen max-w-md bg-white p-4">
      <header className="flex items-center justify-between gap-2">
        <Link to="/app" className="text-sm text-gray-600">
          ← 홈
        </Link>
        <SessionBar />
      </header>
      {applications.isPending && <p className="mt-6 text-sm text-gray-500">불러오는 중…</p>}
      {applications.isSuccess && !application && (
        <p className="mt-6 text-sm text-gray-500">이 계정의 신청이 아니에요. 테스트 계정을 확인해주세요.</p>
      )}
      {application && <Detail application={application} />}
    </main>
  )
}

function Detail({ application }: { application: ApplicationItem }) {
  const queryClient = useQueryClient()
  const pay = useMutation({
    mutationFn: () => api.mockApprove(application.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-applications'] }),
  })
  const confirmed = application.matchStatus === 'CONFIRMED'

  return (
    <>
      <section className="mt-4 rounded-lg border border-gray-200 p-4">
        <h1 className="text-lg font-bold">신청 #{application.id}</h1>
        <div className="mt-2">
          <StatusBadges application={application} />
        </div>
        <ol className="mt-3 space-y-1 text-sm">
          {application.slotChoices.map((c) => (
            <li key={c.priority}>
              {c.priority}순위 · {c.slot?.regionName} {formatDateTime(c.slot?.startsAt)}
              {application.assignedSlotId === c.slot?.id && (
                <span className="ml-1 text-xs font-semibold text-green-700">확정</span>
              )}
            </li>
          ))}
        </ol>
        <NextStep application={application} />
        {application.paymentStatus === 'PENDING' && (
          <>
            <p className="mt-3 text-xs text-gray-500">결제 기한 {formatDateTime(application.paymentDueAt)}</p>
            <p className="mt-1 text-xs text-gray-500">진행 2일 전부터 환불 불가, 당일 취소 및 단체방 퇴장 불가</p>
            {pay.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(pay.error)}</p>}
            <button
              type="button"
              disabled={pay.isPending}
              onClick={() => pay.mutate()}
              className="mt-2 w-full rounded-lg bg-amber-800 py-3 text-sm font-semibold text-white disabled:bg-gray-300"
            >
              결제하기 (테스트 결제 9,900원)
            </button>
          </>
        )}
      </section>
      {RESOLUTION_STATUSES.includes(application.matchStatus ?? '') && (
        <ResolutionSection applicationId={application.id} />
      )}
      {confirmed && <TableSection applicationId={application.id} />}
    </>
  )
}

// 대기 상태에서도 다음에 무슨 일이 일어나는지 보여준다 (STA-02)
function NextStep({ application }: { application: ApplicationItem }) {
  if (application.paymentStatus === 'PENDING') {
    return <p className="mt-3 text-sm text-gray-700">결제를 마치면 매칭 대기열에 들어가요.</p>
  }
  if (application.matchStatus === 'WAITING') {
    const first = application.slotChoices[0]?.slot
    return (
      <p className="mt-3 text-sm text-gray-700">
        매칭을 기다리고 있어요. 매칭 발표 예정: {formatDateTime(first?.matchRunAt)}
      </p>
    )
  }
  return null
}

// 단체방 시스템 메시지 종류별 표시 (INF-05·07, CON-03). 기능만, 디자인 없음.
const MESSAGE_LABEL: Record<string, string> = {
  NOTICE: '공지',
  CURATION: '테이블 소개',
  REMINDER: '리마인드',
  CONTENT: '대화 주제',
}
const MESSAGE_STYLE: Record<string, string> = {
  NOTICE: 'bg-blue-50',
  CURATION: 'bg-green-50',
  REMINDER: 'bg-yellow-50',
  CONTENT: 'bg-purple-50',
}

function TableSection({ applicationId }: { applicationId: number }) {
  const table = useQuery({ queryKey: ['my-table', applicationId], queryFn: () => api.myTable(applicationId) })
  const chatRoomId = table.data?.chatRoomId
  const messages = useQuery({
    queryKey: ['chat', chatRoomId],
    queryFn: () => api.chatMessages(chatRoomId!),
    enabled: chatRoomId != null,
  })

  return (
    <>
      <section className="mt-4 rounded-lg border border-green-200 bg-green-50 p-4">
        <h2 className="font-semibold text-green-900">테이블이 확정됐어요</h2>
        {table.data && (
          <>
            <p className="mt-1 text-sm text-green-900">{table.data.memberCount}명이 함께해요.</p>
            <ul className="mt-2 list-disc pl-5 text-xs text-green-800">
              {table.data.reasons.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          </>
        )}
      </section>

      <section className="mt-4 rounded-lg border border-gray-200 p-4">
        <h2 className="font-semibold">단체방</h2>
        <ul className="mt-2 space-y-2">
          {messages.data?.map((m) => (
            <li key={m.id} className={`rounded-lg p-3 text-sm ${MESSAGE_STYLE[m.messageType] ?? 'bg-gray-100'}`}>
              <span className="block text-xs text-gray-500">
                {m.senderType === 'SYSTEM' ? '우연한 식탁' : '참가자'}
                {MESSAGE_LABEL[m.messageType] ? ` · ${MESSAGE_LABEL[m.messageType]}` : ''} · {formatDateTime(m.createdAt)}
              </span>
              <span className="whitespace-pre-line">{m.body}</span>
            </li>
          ))}
        </ul>
      </section>

      <AttendanceSection applicationId={applicationId} />
    </>
  )
}

// 체크인 (7.1, FDB-01): 행사 시작 30분 전부터 종료까지. 체크인한 사람만 피드백을 남길 수 있다
function AttendanceSection({ applicationId }: { applicationId: number }) {
  const queryClient = useQueryClient()
  const attendance = useQuery({
    queryKey: ['attendance', applicationId],
    queryFn: () => api.attendance(applicationId),
    retry: false,
  })
  const checkIn = useMutation({
    mutationFn: () => api.checkIn(applicationId),
    onSuccess: (data) => queryClient.setQueryData(['attendance', applicationId], data),
  })
  const a = attendance.data
  if (!a) return null

  return (
    <>
      <section className="mt-4 rounded-lg border border-gray-200 p-4 text-sm">
        <h2 className="font-semibold">체크인</h2>
        {a.attendanceStatus === 'ATTENDED' ? (
          <p className="mt-1 text-green-800">체크인 완료 · {formatDateTime(a.checkedInAt)}</p>
        ) : a.attendanceStatus === 'SCHEDULED' ? (
          <>
            <p className="mt-1 text-gray-700">
              {formatDateTime(a.checkInOpensAt)} ~ {formatDateTime(a.checkInClosesAt)}에 체크인할 수 있어요.
            </p>
            {checkIn.isError && <p className="mt-2 text-red-700">{errorMessage(checkIn.error)}</p>}
            <button
              type="button"
              disabled={!a.canCheckIn || checkIn.isPending}
              onClick={() => checkIn.mutate()}
              className="mt-2 w-full rounded-lg bg-amber-800 py-3 font-semibold text-white disabled:bg-gray-300"
            >
              체크인
            </button>
          </>
        ) : (
          <p className="mt-1 text-gray-700">참석 상태: {a.attendanceStatus}</p>
        )}
      </section>
      {a.attendanceStatus === 'ATTENDED' && <FeedbackSection applicationId={applicationId} />}
    </>
  )
}

const RATING_FIELDS = [
  { key: 'tableRating', label: '테이블 분위기', max: 5, min: 1 },
  { key: 'conversationRating', label: '대화', max: 5, min: 1 },
  { key: 'venueRating', label: '식당', max: 5, min: 1 },
  { key: 'rejoinIntent', label: '다시 참여하고 싶은 정도', max: 5, min: 1 },
  { key: 'recommendScore', label: '지인에게 추천하고 싶은 정도 (0~10)', max: 10, min: 0 },
] as const
type RatingKey = (typeof RATING_FIELDS)[number]['key']

function FeedbackSection({ applicationId }: { applicationId: number }) {
  const queryClient = useQueryClient()
  const mine = useQuery({ queryKey: ['my-feedbacks'], queryFn: api.myFeedbacks })
  const submitted = mine.data?.find((f) => f.applicationId === applicationId)
  const [ratings, setRatings] = useState<Record<RatingKey, number>>({
    tableRating: 5,
    conversationRating: 5,
    venueRating: 5,
    rejoinIntent: 5,
    recommendScore: 8,
  })
  const [comment, setComment] = useState('')
  const submit = useMutation({
    mutationFn: () => api.submitFeedback({ applicationId, ...ratings, comment: comment || undefined }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-feedbacks'] }),
  })

  return (
    <>
      {submitted ? (
        <section className="mt-4 rounded-lg border border-gray-200 p-4 text-sm">
          <h2 className="font-semibold">피드백</h2>
          <p className="mt-1 text-gray-700">
            제출했어요 · 분위기 {submitted.tableRating} · 대화 {submitted.conversationRating} · 식당{' '}
            {submitted.venueRating} · 재참여 {submitted.rejoinIntent ?? '-'} · 추천 {submitted.recommendScore ?? '-'}
          </p>
        </section>
      ) : (
        <section className="mt-4 rounded-lg border border-gray-200 p-4">
          <h2 className="font-semibold">식사는 어땠나요?</h2>
          {RATING_FIELDS.map((f) => (
            <div key={f.key} className="mt-3">
              <p className="text-sm text-gray-700">{f.label}</p>
              <div className="mt-1 flex flex-wrap gap-1">
                {Array.from({ length: f.max - f.min + 1 }, (_, i) => f.min + i).map((n) => (
                  <button
                    key={n}
                    type="button"
                    onClick={() => setRatings((r) => ({ ...r, [f.key]: n }))}
                    className={`h-10 min-w-9 flex-1 rounded border text-sm ${
                      ratings[f.key] === n ? 'border-amber-800 bg-amber-800 text-white' : 'border-gray-300'
                    }`}
                  >
                    {n}
                  </button>
                ))}
              </div>
            </div>
          ))}
          <textarea
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            placeholder="남기고 싶은 말 (선택)"
            className="mt-3 w-full rounded border border-gray-300 p-2 text-sm"
            rows={2}
          />
          {submit.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(submit.error)}</p>}
          <button
            type="button"
            disabled={submit.isPending}
            onClick={() => submit.mutate()}
            className="mt-2 w-full rounded-lg bg-amber-800 py-3 text-sm font-semibold text-white disabled:bg-gray-300"
          >
            피드백 보내기
          </button>
        </section>
      )}
      <PreferenceSection applicationId={applicationId} />
    </>
  )
}

// 다시 만나고 싶은/싫은 사람 (FDB-04). 이름만 보이고, 선택은 본인에게만 보이며 상대에게는 알려지지 않는다
function PreferenceSection({ applicationId }: { applicationId: number }) {
  const queryClient = useQueryClient()
  const companions = useQuery({ queryKey: ['companions', applicationId], queryFn: () => api.companions(applicationId) })
  const saved = useQuery({
    queryKey: ['person-preferences', applicationId],
    queryFn: () => api.personPreferences(applicationId),
  })
  const [draft, setDraft] = useState<Record<number, PreferenceType | undefined>>()
  const current: Record<number, PreferenceType | undefined> =
    draft ?? Object.fromEntries((saved.data ?? []).map((c) => [c.companionId, c.preference]))
  const save = useMutation({
    mutationFn: () =>
      api.savePersonPreferences(
        applicationId,
        Object.entries(current).flatMap(([id, preference]) =>
          preference ? [{ companionId: Number(id), preference }] : [],
        ),
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(['person-preferences', applicationId], data)
      setDraft(undefined)
    },
  })
  if (!companions.data?.length) return null

  const options: { value: PreferenceType | undefined; label: string }[] = [
    { value: undefined, label: '선택 안 함' },
    { value: 'WANT_AGAIN', label: '다시 만나고 싶어요' },
    { value: 'AVOID', label: '다시 만나고 싶지 않아요' },
  ]

  return (
    <section className="mt-4 rounded-lg border border-gray-200 p-4 text-sm">
      <h2 className="font-semibold">함께한 분들은 어땠나요?</h2>
      <p className="mt-1 text-xs text-gray-500">선택은 나만 볼 수 있고, 상대에게는 절대 알려지지 않아요.</p>
      <ul className="mt-2 space-y-3">
        {companions.data.map((c) => (
          <li key={c.companionId}>
            <p className="font-medium">{c.name}</p>
            <div className="mt-1 flex flex-wrap gap-1">
              {options.map((o) => (
                <button
                  key={o.label}
                  type="button"
                  onClick={() => setDraft({ ...current, [c.companionId]: o.value })}
                  className={`rounded border px-2 py-2 text-xs ${
                    current[c.companionId] === o.value
                      ? 'border-amber-800 bg-amber-800 text-white'
                      : 'border-gray-300'
                  }`}
                >
                  {o.label}
                </button>
              ))}
            </div>
          </li>
        ))}
      </ul>
      {save.isError && <p className="mt-2 text-red-700">{errorMessage(save.error)}</p>}
      {save.isSuccess && !draft && <p className="mt-2 text-green-800">저장했어요.</p>}
      <button
        type="button"
        disabled={save.isPending || !draft}
        onClick={() => save.mutate()}
        className="mt-3 w-full rounded-lg bg-amber-800 py-3 font-semibold text-white disabled:bg-gray-300"
      >
        선택 저장
      </button>
    </section>
  )
}

// 매칭 실패 후 선택 (6.1, STA-04~06): 추천 일정 이동 · 이용권 보관 · 환불. 기능만, 디자인 없음
const RESOLUTION_STATUSES = ['ALTERNATIVE_OFFERED', 'NO_MATCH', 'TRANSFERRED']

const CHOICE_LABEL: Record<ResolutionChoice, string> = {
  TRANSFER: '추천 일정으로 이동',
  KEEP: '이용권 보관',
  REFUND: '환불',
}

function ResolutionSection({ applicationId }: { applicationId: number }) {
  const queryClient = useQueryClient()
  const resolution = useQuery({
    queryKey: ['resolution', applicationId],
    queryFn: () => api.resolution(applicationId),
    retry: false,
  })
  const choose = useMutation({
    mutationFn: ({ choice, slotId }: { choice: ResolutionChoice; slotId?: number }) =>
      api.chooseResolution(applicationId, choice, slotId),
    onSuccess: (data) => {
      queryClient.setQueryData(['resolution', applicationId], data)
      queryClient.invalidateQueries({ queryKey: ['my-applications'] })
    },
  })
  const r = resolution.data
  if (!r) return null
  const can = (c: ResolutionChoice) => r.choices.find((x) => x.choice === c)
  const transfer = can('TRANSFER')
  const keep = can('KEEP')
  const refund = can('REFUND')

  return (
    <section className="mt-4 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm">
      <h2 className="font-semibold text-amber-900">이번 일정은 매칭되지 않았어요</h2>
      {r.matchFailReason && <p className="mt-1 text-amber-900">{r.matchFailReason}</p>}

      {r.resolutionStatus === 'OFFERED' ? (
        <>
          <p className="mt-2 text-xs text-gray-600">
            {formatDateTime(r.respondBy)}까지 고르지 않으면 이용권으로 보관돼요.
          </p>
          <h3 className="mt-3 font-semibold">추천 일정</h3>
          {r.offeredSlots.length === 0 && <p className="mt-1 text-gray-600">추천할 수 있는 일정이 없어요.</p>}
          <ul className="mt-1 space-y-2">
            {r.offeredSlots.map((o) => (
              <li key={o.slot.id}>
                <button
                  type="button"
                  disabled={!o.available || !transfer?.available || choose.isPending}
                  onClick={() => choose.mutate({ choice: 'TRANSFER', slotId: o.slot.id })}
                  className="w-full rounded-lg border border-amber-800 bg-white p-3 text-left disabled:border-gray-300 disabled:text-gray-400"
                >
                  {o.slot.regionName} {formatDateTime(o.slot.startsAt)}
                  <span className="ml-1 text-xs text-gray-500">({o.reason})</span>
                </button>
              </li>
            ))}
          </ul>
          {transfer && <p className="mt-1 text-xs text-gray-500">{transfer.description}</p>}
          {[keep, refund].map(
            (c) =>
              c && (
                <div key={c.choice} className="mt-3">
                  <button
                    type="button"
                    disabled={!c.available || choose.isPending}
                    onClick={() => choose.mutate({ choice: c.choice })}
                    className="w-full rounded-lg bg-amber-800 py-3 font-semibold text-white disabled:bg-gray-300"
                  >
                    {CHOICE_LABEL[c.choice]}
                  </button>
                  <p className="mt-1 text-xs text-gray-500">{c.description}</p>
                </div>
              ),
          )}
          {choose.isError && <p className="mt-2 text-red-700">{errorMessage(choose.error)}</p>}
        </>
      ) : (
        <p className="mt-2 text-gray-800">
          {r.resolutionStatus === 'AUTO_KEPT'
            ? '기한까지 선택하지 않아 이용권으로 보관했어요.'
            : `${CHOICE_LABEL[r.choice!]}을 선택했어요.`}
          {r.choice === 'TRANSFER' && r.newApplicationId && (
            <Link to={`/app/applications/${r.newApplicationId}`} className="ml-1 underline">
              새 신청 보기
            </Link>
          )}
          {r.choice === 'REFUND' && r.paymentStatus && (
            <span className="ml-1">({PAYMENT_STATUS[r.paymentStatus] ?? r.paymentStatus})</span>
          )}
        </p>
      )}
    </section>
  )
}
