import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { ApplicationItem, PreferenceType, ReportReason, ResolutionChoice } from '../../api/types'
import ApplicationHero from '../../components/ApplicationHero'
import SessionBar from '../../components/SessionBar'
import StatusBadges from '../../components/StatusBadges'
import {
  AppShell,
  EmptyState,
  ErrorText,
  inputCls,
  LinkButton,
  PrimaryButton,
  SecondaryButton,
  SectionHeader,
  SlotTicket,
} from '../../components/ui'
import { RESOLUTION_STATUSES } from '../../lib/design'
import { formatDateTime, PAYMENT_STATUS } from '../../lib/format'
import { useServerNow, useTableInfo } from '../../lib/useTableInfo'

// 신청 상세 (/app/applications/:id): 상태 → 결제 → 확정 테이블·단체방 → 피드백
export default function ApplicationDetailPage() {
  const id = Number(useParams().id)
  const applications = useQuery({ queryKey: ['my-applications'], queryFn: api.myApplications })
  const application = applications.data?.find((a) => a.id === id)

  return (
    <AppShell>
      {applications.isPending && <p className="px-[22px] pt-6 text-sm text-muted">불러오는 중…</p>}
      {applications.isSuccess && !application && (
        <div className="px-[22px] pt-6">
          <p className="text-sm text-muted">이 계정의 신청이 아니에요. 테스트 계정을 확인해주세요.</p>
          <Link to="/app" className="mt-2 flex min-h-[44px] items-center font-bold text-accent">
            ← 홈으로
          </Link>
        </div>
      )}
      {application && <Detail application={application} />}
    </AppShell>
  )
}

function Detail({ application }: { application: ApplicationItem }) {
  const queryClient = useQueryClient()
  const nowMs = useServerNow()
  const confirmed = application.matchStatus === 'CONFIRMED'
  const { table } = useTableInfo(application.id, confirmed)
  const pay = useMutation({
    mutationFn: () => api.mockApprove(application.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['my-applications'] }),
  })
  const resolving = RESOLUTION_STATUSES.includes(application.matchStatus ?? '')

  return (
    <>
      <ApplicationHero application={application} nowMs={nowMs} memberCount={table.data?.memberCount} />

      <div className="flex flex-col gap-5 px-[18px] pt-5">
        <section className="flex flex-col gap-2.5">
          <SectionHeader title={`Application #${application.id}`} label="신청 내역" />
          <StatusBadges application={application} />
          <div className="flex flex-col gap-2">
            {application.slotChoices.map(
              (c) =>
                c.slot && (
                  <SlotTicket
                    key={c.priority}
                    startsAt={c.slot.startsAt}
                    title={`${c.priority}순위 · ${c.slot.regionName}`}
                    sub={formatDateTime(c.slot.startsAt)}
                    stampTone={application.assignedSlotId === c.slot.id ? 'accent' : 'ink'}
                    selected={application.assignedSlotId === c.slot.id}
                    badge={
                      application.assignedSlotId === c.slot.id ? (
                        <span className="bg-sage px-2 py-0.5 text-[11px] font-bold">확정</span>
                      ) : undefined
                    }
                  />
                ),
            )}
          </div>
          <NextStep application={application} />
          {application.paymentStatus === 'PENDING' && (
            <>
              <p className="text-[11px] text-muted">진행 2일 전부터 환불 불가, 당일 취소 및 단체방 퇴장 불가</p>
              {pay.isError && <ErrorText>{errorMessage(pay.error)}</ErrorText>}
              <PrimaryButton disabled={pay.isPending} onClick={() => pay.mutate()}>
                결제하기 (테스트 결제 9,900원)
              </PrimaryButton>
            </>
          )}
        </section>

        {resolving && <ResolutionSection applicationId={application.id} />}
        {confirmed && (
          <>
            <LinkButton to={`/app/applications/${application.id}/table`}>
              <span>단체방 입장하기</span>
              <span>→</span>
            </LinkButton>
            <AttendanceSection applicationId={application.id} />
          </>
        )}

        <div className="border-t-[1.5px] border-ink pt-3">
          <SessionBar />
        </div>
      </div>
    </>
  )
}

// 대기 상태에서도 다음에 무슨 일이 일어나는지 보여준다 (STA-02)
function NextStep({ application }: { application: ApplicationItem }) {
  if (application.paymentStatus === 'PENDING') {
    return <p className="text-sm text-body">결제를 마치면 매칭 대기열에 들어가요.</p>
  }
  if (application.matchStatus === 'WAITING') {
    const first = application.slotChoices[0]?.slot
    return <p className="text-sm text-body">매칭을 기다리고 있어요. 매칭 발표 예정: {formatDateTime(first?.matchRunAt)}</p>
  }
  return null
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
      <section className="flex flex-col gap-2.5 text-sm">
        <SectionHeader title="Check-in" label="체크인" />
        {a.attendanceStatus === 'ATTENDED' ? (
          <p className="border-[1.5px] border-ink bg-sage px-3 py-3 font-bold">체크인 완료 · {formatDateTime(a.checkedInAt)}</p>
        ) : a.attendanceStatus === 'SCHEDULED' ? (
          <>
            <p className="text-body">
              {formatDateTime(a.checkInOpensAt)} ~ {formatDateTime(a.checkInClosesAt)}에 체크인할 수 있어요.
            </p>
            {checkIn.isError && <ErrorText>{errorMessage(checkIn.error)}</ErrorText>}
            <PrimaryButton disabled={!a.canCheckIn || checkIn.isPending} onClick={() => checkIn.mutate()}>
              체크인
            </PrimaryButton>
          </>
        ) : (
          <p className="text-body">참석 상태: {a.attendanceStatus}</p>
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
        <section className="flex flex-col gap-2.5 text-sm">
          <SectionHeader title="Feedback" label="피드백" />
          <p className="text-body">
            제출했어요 · 분위기 {submitted.tableRating} · 대화 {submitted.conversationRating} · 식당{' '}
            {submitted.venueRating} · 재참여 {submitted.rejoinIntent ?? '-'} · 추천 {submitted.recommendScore ?? '-'}
          </p>
        </section>
      ) : (
        <section className="flex flex-col gap-3">
          <SectionHeader title="How was dinner?" label="피드백" />
          {RATING_FIELDS.map((f) => (
            <div key={f.key}>
              <p className="text-sm text-body">{f.label}</p>
              <div className="mt-1 flex flex-wrap gap-1">
                {Array.from({ length: f.max - f.min + 1 }, (_, i) => f.min + i).map((n) => (
                  <button
                    key={n}
                    type="button"
                    onClick={() => setRatings((r) => ({ ...r, [f.key]: n }))}
                    aria-pressed={ratings[f.key] === n}
                    className={`min-h-[44px] min-w-[36px] flex-1 border-[1.5px] border-ink text-sm font-bold ${
                      ratings[f.key] === n ? 'bg-ink text-cream' : 'bg-card'
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
            className={`${inputCls} py-2`}
            rows={2}
          />
          {submit.isError && <ErrorText>{errorMessage(submit.error)}</ErrorText>}
          <PrimaryButton disabled={submit.isPending} onClick={() => submit.mutate()}>
            피드백 보내기
          </PrimaryButton>
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
    <section className="flex flex-col gap-2.5 text-sm">
      <SectionHeader title="Who sat with you" label="동석자" />
      <p className="text-xs text-muted">선택은 나만 볼 수 있고, 상대에게는 절대 알려지지 않아요.</p>
      <ul className="flex flex-col gap-4">
        {companions.data.map((c) => (
          <li key={c.companionId}>
            <p className="font-serif font-black">{c.name}</p>
            <div className="mt-1 flex flex-wrap gap-1">
              {options.map((o) => (
                <button
                  key={o.label}
                  type="button"
                  onClick={() => setDraft({ ...current, [c.companionId]: o.value })}
                  aria-pressed={current[c.companionId] === o.value}
                  className={`min-h-[44px] border-[1.5px] border-ink px-3 text-xs font-bold ${
                    current[c.companionId] === o.value ? 'bg-ink text-cream' : 'bg-card'
                  }`}
                >
                  {o.label}
                </button>
              ))}
            </div>
            <SafetyActions applicationId={applicationId} companionId={c.companionId} />
          </li>
        ))}
      </ul>
      {save.isError && <ErrorText>{errorMessage(save.error)}</ErrorText>}
      {save.isSuccess && !draft && <p className="font-bold text-accent">저장했어요.</p>}
      <PrimaryButton disabled={save.isPending || !draft} onClick={() => save.mutate()}>
        선택 저장
      </PrimaryButton>
    </section>
  )
}

const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'HARASSMENT', label: '괴롭힘·불쾌한 언행' },
  { value: 'INAPPROPRIATE_BEHAVIOR', label: '부적절한 행동' },
  { value: 'PRIVACY_VIOLATION', label: '개인정보 침해' },
  { value: 'SAFETY_THREAT', label: '안전 위협' },
  { value: 'OTHER', label: '기타' },
]

// 동석자 차단·신고 (SAF-01). 접수되면 다음 매칭부터 분리되고, 상대에게는 알려지지 않는다.
function SafetyActions({ applicationId, companionId }: { applicationId: number; companionId: number }) {
  const [reporting, setReporting] = useState(false)
  const [reason, setReason] = useState<ReportReason>('HARASSMENT')
  const [description, setDescription] = useState('')
  const block = useMutation({ mutationFn: () => api.blockCompanion(applicationId, companionId) })
  const report = useMutation({
    mutationFn: () => api.reportCompanion({ applicationId, companionId, reasonType: reason, description }),
    onSuccess: () => setReporting(false),
  })

  return (
    <div className="mt-1 text-xs">
      <button
        type="button"
        disabled={block.isPending || block.isSuccess}
        onClick={() => window.confirm('이 분을 차단할까요? 다음 매칭에서 만나지 않아요.') && block.mutate()}
        className="mr-3 min-h-[44px] text-muted underline"
      >
        {block.isSuccess ? '차단했어요' : '차단'}
      </button>
      <button
        type="button"
        disabled={report.isSuccess}
        onClick={() => setReporting(!reporting)}
        className="min-h-[44px] text-muted underline"
      >
        {report.isSuccess ? '신고를 접수했어요' : '신고'}
      </button>
      {reporting && (
        <div className="mt-1 flex flex-col gap-2 border-[1.5px] border-ink bg-card p-2">
          <select value={reason} onChange={(e) => setReason(e.target.value as ReportReason)} className={inputCls}>
            {REPORT_REASONS.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={2000}
            placeholder="어떤 일이 있었는지 알려주세요. 상대에게는 공개되지 않아요."
            className={`${inputCls} py-2`}
          />
          <button
            type="button"
            disabled={report.isPending || !description.trim()}
            onClick={() => report.mutate()}
            className="min-h-[44px] bg-accent font-bold text-cream disabled:opacity-40"
          >
            신고하기
          </button>
        </div>
      )}
      {(block.isError || report.isError) && <ErrorText>{errorMessage(block.error ?? report.error)}</ErrorText>}
    </div>
  )
}

// 매칭 실패 후 선택 (6.1, STA-04~06): 추천 일정 이동 · 이용권 보관 · 환불 (Resolve.html)
const CHOICE_LABEL: Record<ResolutionChoice, string> = {
  TRANSFER: '추천 일정으로 이동',
  KEEP: '이용권 보관',
  REFUND: '바로 환불',
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
    <section className="flex flex-col gap-3">
      {r.matchFailReason && (
        <p className="px-1 text-[13px] leading-[1.7] text-body">
          {r.matchFailReason} <b className="text-ink">결제는 그대로</b>, 추가 비용 없이 옮길 수 있어요.
        </p>
      )}

      {r.resolutionStatus === 'OFFERED' ? (
        <>
          <SectionHeader title="Other evenings" label="추천 일정" />
          {r.offeredSlots.length === 0 && <EmptyState>추천할 수 있는 일정이 없어요.</EmptyState>}
          <ul className="flex flex-col gap-2.5">
            {r.offeredSlots.map((o, i) => (
              <li key={o.slot.id}>
                <SlotTicket
                  startsAt={o.slot.startsAt}
                  title={o.slot.regionName}
                  sub={o.reason}
                  stampTone={i === 0 ? 'accent' : 'ink'}
                  selected={i === 0}
                  disabled={!o.available || !transfer?.available || choose.isPending}
                  onClick={() => choose.mutate({ choice: 'TRANSFER', slotId: o.slot.id })}
                  badge={<span className="shrink-0 text-[13px] font-bold text-accent">이동 →</span>}
                />
              </li>
            ))}
          </ul>
          {transfer && <p className="text-[11px] text-muted">{transfer.description}</p>}

          <div className="grid grid-cols-2 gap-2">
            {keep && (
              <SecondaryButton disabled={!keep.available || choose.isPending} onClick={() => choose.mutate({ choice: 'KEEP' })}>
                {CHOICE_LABEL.KEEP}
              </SecondaryButton>
            )}
            {refund && (
              <PrimaryButton
                disabled={!refund.available || choose.isPending}
                onClick={() => choose.mutate({ choice: 'REFUND' })}
              >
                {CHOICE_LABEL.REFUND}
              </PrimaryButton>
            )}
          </div>
          {[keep, refund].map((c) => c && <p key={c.choice} className="text-[11px] text-muted">{c.description}</p>)}
          <p className="text-center text-[11px] text-muted">
            {formatDateTime(r.respondBy)}까지 선택하지 않으면 이용권으로 보관돼요
          </p>
          {choose.isError && <ErrorText>{errorMessage(choose.error)}</ErrorText>}
        </>
      ) : (
        <p className="border-[1.5px] border-ink bg-card p-3 text-sm text-ink">
          {r.resolutionStatus === 'AUTO_KEPT'
            ? '기한까지 선택하지 않아 이용권으로 보관했어요.'
            : `${CHOICE_LABEL[r.choice!]}을 선택했어요.`}
          {r.choice === 'TRANSFER' && r.newApplicationId && (
            <Link to={`/app/applications/${r.newApplicationId}`} className="ml-1 font-bold text-accent underline">
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
