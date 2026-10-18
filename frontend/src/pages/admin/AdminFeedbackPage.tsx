import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { FeedbackAverages } from '../../api/types'
import AdminLayout from '../../components/AdminLayout'
import { formatDateTime } from '../../lib/format'
import { ReportRow } from './AdminReportsPage'

const fmt = (n: number | null) => (n == null ? '-' : n.toFixed(2))

// 피드백 요약 + 신고 검토 (PRD 11장 피드백·안전, 16장)
// 사람별 선호(AVOID/WANT_AGAIN)는 집계 숫자만 보여주고, 누가 누구를 골랐는지는 API가 내려주지 않는다.
export default function AdminFeedbackPage() {
  const slots = useQuery({ queryKey: ['admin-slots'], queryFn: api.adminSlots })
  const [slotId, setSlotId] = useState<number | undefined>()
  const [onlyOpen, setOnlyOpen] = useState(true)
  const summary = useQuery({ queryKey: ['feedback-summary', slotId], queryFn: () => api.feedbackSummary(slotId) })
  const reports = useQuery({ queryKey: ['admin-reports', onlyOpen], queryFn: () => api.adminReports(onlyOpen) })

  return (
    <AdminLayout title="피드백 · 신고">
      <div className="mt-4 flex items-center gap-2 text-sm">
        <select
          className="rounded border border-gray-300 bg-white px-2 py-1"
          value={slotId ?? ''}
          onChange={(e) => setSlotId(e.target.value ? Number(e.target.value) : undefined)}
        >
          <option value="">전체 회차</option>
          {slots.data?.map((s) => (
            <option key={s.id} value={s.id}>
              #{s.id} {s.regionName} · {formatDateTime(s.startsAt)}
            </option>
          ))}
        </select>
      </div>
      {summary.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(summary.error)}</p>}
      {summary.data && (
        <>
          <section className="mt-4 rounded-lg border border-gray-200 bg-white p-4">
            <h2 className="font-semibold">만족도 평균 {summary.data.overall.count === 0 && '(피드백 없음)'}</h2>
            <Averages value={summary.data.overall} />
            <p className="mt-2 text-sm text-gray-600">
              다시 만나고 싶은 사람 {summary.data.wantAgainCount}건 · 피하고 싶은 사람 {summary.data.avoidCount}건
              <span className="text-xs text-gray-400"> (집계만, 개인 식별 불가)</span>
            </p>
          </section>

          <section className="mt-4">
            <h2 className="font-semibold">테이블별</h2>
            <div className="mt-2 overflow-x-auto rounded-lg border border-gray-200 bg-white">
              <table className="min-w-full text-sm">
                <thead className="bg-gray-100">
                  <tr>
                    {['테이블', '회차', '응답', '분위기', '대화', '식당', '재참여', '추천', '다시 만남', '피함'].map((h) => (
                      <th key={h} className="whitespace-nowrap px-2 py-1.5 text-left font-medium">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {summary.data.tables.map((t) => (
                    <tr key={t.tableId}>
                      <td className="px-2 py-1.5">#{t.tableId}</td>
                      <td className="px-2 py-1.5">#{t.slotId}</td>
                      <td className="px-2 py-1.5">{t.averages.count}</td>
                      <td className="px-2 py-1.5">{fmt(t.averages.table)}</td>
                      <td className="px-2 py-1.5">{fmt(t.averages.conversation)}</td>
                      <td className="px-2 py-1.5">{fmt(t.averages.venue)}</td>
                      <td className="px-2 py-1.5">{fmt(t.averages.rejoin)}</td>
                      <td className="px-2 py-1.5">{fmt(t.averages.recommend)}</td>
                      <td className="px-2 py-1.5">{t.wantAgainCount}</td>
                      <td className="px-2 py-1.5">{t.avoidCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="mt-4">
            <h2 className="font-semibold">코멘트 ({summary.data.comments.length})</h2>
            {summary.data.comments.length === 0 && <p className="mt-2 text-sm text-gray-500">코멘트가 없어요.</p>}
            <ul className="mt-2 space-y-2">
              {summary.data.comments.map((c, i) => (
                <li key={i} className="rounded border border-gray-200 bg-white p-3 text-sm">
                  <p className="text-xs text-gray-500">
                    테이블 #{c.tableId} · 분위기 {c.tableRating}점 · {formatDateTime(c.submittedAt)}
                  </p>
                  <p className="mt-1 whitespace-pre-wrap">{c.comment}</p>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}

      <section className="mt-8">
        <div className="flex items-center gap-4">
          <h2 className="font-semibold">신고</h2>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={onlyOpen} onChange={(e) => setOnlyOpen(e.target.checked)} />
            미처리만 보기
          </label>
        </div>
        {reports.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(reports.error)}</p>}
        {reports.data?.length === 0 && <p className="mt-2 text-sm text-gray-500">신고가 없어요.</p>}
        <ul className="mt-2 space-y-3">{reports.data?.map((r) => <ReportRow key={r.id} report={r} />)}</ul>
      </section>
    </AdminLayout>
  )
}

function Averages({ value }: { value: FeedbackAverages }) {
  const items: [string, number | null][] = [
    ['테이블 분위기', value.table],
    ['대화', value.conversation],
    ['식당', value.venue],
    ['재참여 의향', value.rejoin],
    ['추천 의향 (0~10)', value.recommend],
  ]
  return (
    <dl className="mt-3 grid grid-cols-2 gap-3 text-sm md:grid-cols-5">
      {items.map(([name, v]) => (
        <div key={name}>
          <dt className="text-gray-500">{name}</dt>
          <dd className="text-lg font-semibold">{fmt(v)}</dd>
        </div>
      ))}
    </dl>
  )
}
