import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../../api/endpoints'
import SessionBar from '../../components/SessionBar'
import { formatDateTime } from '../../lib/format'

const ATTENDANCE_LABEL: Record<string, string> = {
  ATTENDED: '참석',
  NO_SHOW: '불참(노쇼)',
  CANCELED_EARLY: '사전 취소',
  CANCELED_LATE: '직전 취소',
  SCHEDULED: '예정',
}

// 참가 이력(RET-01)과 다음 일정 추천(RET-02). 기능만, 디자인 없음
export default function HistoryPage() {
  const history = useQuery({ queryKey: ['my-history'], queryFn: api.myHistory })
  const recommendations = useQuery({ queryKey: ['my-recommendations'], queryFn: api.myRecommendations })

  return (
    <main className="mx-auto min-h-screen max-w-md bg-white p-4">
      <header className="flex items-center justify-between gap-2">
        <h1 className="text-xl font-bold">참가 이력</h1>
        <SessionBar />
      </header>
      <Link to="/app" className="mt-2 inline-block text-sm text-amber-800 underline">
        ← 홈
      </Link>

      <section className="mt-4">
        {history.isPending && <p className="text-sm text-gray-500">불러오는 중…</p>}
        {history.data?.length === 0 && <p className="text-sm text-gray-500">아직 참가한 식탁이 없어요.</p>}
        <ul className="space-y-2">
          {history.data?.map((h) => (
            <li key={h.applicationId} className="rounded-lg border border-gray-200 p-3 text-sm">
              <p className="font-medium">
                {h.regionName} · {formatDateTime(h.startsAt)}
              </p>
              <p className="text-gray-600">{h.venueName ?? '식당 미정'}</p>
              <p className="mt-1 text-xs text-gray-500">
                {ATTENDANCE_LABEL[h.attendanceStatus] ?? h.attendanceStatus} ·{' '}
                {h.feedbackSubmitted ? '피드백 제출함' : '피드백 전'}
              </p>
            </li>
          ))}
        </ul>
      </section>

      <section className="mt-6">
        <h2 className="text-sm font-semibold text-gray-500">다음 식탁 추천</h2>
        {recommendations.data?.length === 0 && (
          <p className="mt-2 text-sm text-gray-500">지금 추천할 모집 중 일정이 없어요.</p>
        )}
        <ul className="mt-2 space-y-2">
          {recommendations.data?.map((r) => (
            <li key={r.eventSlotId} className="rounded-lg border border-gray-200 p-3 text-sm">
              <p className="font-medium">
                {r.regionName} · {formatDateTime(r.startsAt)}
              </p>
              <p className="text-xs text-gray-500">
                {[r.sameRegion && '자주 간 지역', r.sameTimeSlot && '비슷한 요일·시간대'].filter(Boolean).join(' · ') ||
                  '가까운 일정'}
              </p>
            </li>
          ))}
        </ul>
        <Link to="/app" className="mt-3 inline-block text-sm text-amber-800 underline">
          신청하러 가기 →
        </Link>
      </section>
    </main>
  )
}
