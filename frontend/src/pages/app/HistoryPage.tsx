import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../../api/endpoints'
import { AppShell, EmptyState, PageTitle, SectionHeader, SlotTicket } from '../../components/ui'

const ATTENDANCE_LABEL: Record<string, string> = {
  ATTENDED: '참석',
  NO_SHOW: '불참(노쇼)',
  CANCELED_EARLY: '사전 취소',
  CANCELED_LATE: '직전 취소',
  SCHEDULED: '예정',
}

// 기록 탭 (/app/history): 참가 이력(RET-01)과 다음 일정 추천(RET-02)
export default function HistoryPage() {
  const history = useQuery({ queryKey: ['my-history'], queryFn: api.myHistory })
  const recommendations = useQuery({ queryKey: ['my-recommendations'], queryFn: api.myRecommendations })

  return (
    <AppShell>
      <PageTitle kicker="Past tables." title="참가 이력" />
      <section className="flex flex-col gap-2.5 px-[18px]">
        {history.isPending && <p className="text-sm text-muted">불러오는 중…</p>}
        {history.data?.length === 0 && <EmptyState>아직 참가한 식탁이 없어요.</EmptyState>}
        {history.data?.map((h) => (
          <SlotTicket
            key={h.applicationId}
            to={`/app/applications/${h.applicationId}`}
            startsAt={h.startsAt}
            title={`${h.regionName} · ${h.venueName ?? '식당 미정'}`}
            sub={`${ATTENDANCE_LABEL[h.attendanceStatus] ?? h.attendanceStatus} · ${h.feedbackSubmitted ? '피드백 제출함' : '피드백 전'}`}
            stampTone="ink"
            badge={
              !h.feedbackSubmitted && h.attendanceStatus === 'ATTENDED' ? (
                <span className="shrink-0 bg-mustard px-2 py-0.5 text-[11px] font-bold">피드백</span>
              ) : undefined
            }
          />
        ))}
      </section>

      <section className="flex flex-col gap-2.5 px-[18px] pt-7">
        <SectionHeader title="Next up" label="다음 식탁 추천" />
        {recommendations.data?.length === 0 && <EmptyState>지금 추천할 모집 중 일정이 없어요.</EmptyState>}
        {recommendations.data?.map((r) => (
          <SlotTicket
            key={r.eventSlotId}
            to="/app/slots"
            startsAt={r.startsAt}
            title={r.regionName}
            sub={
              [r.sameRegion && '자주 간 지역', r.sameTimeSlot && '비슷한 요일·시간대'].filter(Boolean).join(' · ') ||
              '가까운 일정'
            }
            badge={<span className="shrink-0 bg-sage px-2 py-0.5 text-[11px] font-bold">추천</span>}
          />
        ))}
        <Link to="/app/slots" className="flex min-h-[44px] items-center justify-end text-sm font-bold text-accent">
          신청하러 가기 →
        </Link>
      </section>
    </AppShell>
  )
}
