import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { ApplicationItem, ResolutionChoice } from '../../api/types'
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
            <li key={m.id} className="rounded-lg bg-gray-100 p-3 text-sm">
              <span className="block text-xs text-gray-500">
                {m.senderType === 'SYSTEM' ? '우연한 식탁' : '참가자'} · {formatDateTime(m.createdAt)}
              </span>
              {m.body}
            </li>
          ))}
        </ul>
      </section>

      <FeedbackSection applicationId={applicationId} />
    </>
  )
}

function FeedbackSection({ applicationId }: { applicationId: number }) {
  const queryClient = useQueryClient()
  const mine = useQuery({ queryKey: ['my-feedbacks'], queryFn: api.myFeedbacks })
  const submitted = mine.data?.find((f) => f.applicationId === applicationId)
  const [ratings, setRatings] = useState({ tableRating: 5, conversationRating: 5, venueRating: 5 })
  const [comment, setComment] = useState('')
  const submit = useMutation({
    mutationFn: () => api.submitFeedback({ applicationId, ...ratings, comment: comment || undefined }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-feedbacks'] }),
  })

  if (submitted) {
    return (
      <section className="mt-4 rounded-lg border border-gray-200 p-4 text-sm">
        <h2 className="font-semibold">피드백</h2>
        <p className="mt-1 text-gray-700">
          제출했어요 · 분위기 {submitted.tableRating} · 대화 {submitted.conversationRating} · 식당{' '}
          {submitted.venueRating}
        </p>
      </section>
    )
  }

  const fields: { key: keyof typeof ratings; label: string }[] = [
    { key: 'tableRating', label: '테이블 분위기' },
    { key: 'conversationRating', label: '대화' },
    { key: 'venueRating', label: '식당' },
  ]

  return (
    <section className="mt-4 rounded-lg border border-gray-200 p-4">
      <h2 className="font-semibold">식사는 어땠나요?</h2>
      {fields.map((f) => (
        <div key={f.key} className="mt-3">
          <p className="text-sm text-gray-700">{f.label}</p>
          <div className="mt-1 flex gap-1">
            {[1, 2, 3, 4, 5].map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => setRatings((r) => ({ ...r, [f.key]: n }))}
                className={`h-10 flex-1 rounded border text-sm ${
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
