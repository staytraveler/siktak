import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import AdminLayout from '../../components/AdminLayout'
import type { MatchRunItem, PlacementLog } from '../../api/types'
import { DECISION, formatDateTime, TABLE_STATUS } from '../../lib/format'

// 자동 매칭 모니터 (/admin/matching, PRD 11장·MAT-12): 실행 목록 → 테이블 목록 + 참가자별 배치 로그
const SUMMARY_LABELS: [string, string][] = [
  ['candidateCount', '대상'],
  ['confirmedTables', '확정 테이블'],
  ['confirmedMembers', '확정 인원'],
  ['reallocatedCount', '재배치'],
  ['deferredCount', '보류'],
  ['failedCount', '실패'],
  ['excludedCount', '제외'],
  ['venueUnavailable', '식당 없음'],
  ['splitGroups', '분리'],
  ['mergedTables', '병합'],
]

export default function AdminMatchingPage() {
  const runs = useQuery({ queryKey: ['admin-match-runs'], queryFn: api.matchRuns, refetchInterval: 15000 })
  const slots = useQuery({ queryKey: ['admin-slots'], queryFn: api.adminSlots })
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const slotLabel = (id: number | null | undefined) => {
    if (id == null) return '-'
    const s = slots.data?.find((x) => x.id === id)
    return s ? `#${s.id} ${s.regionName}` : `#${id}`
  }
  const selectedRunId = selectedId ?? runs.data?.[0]?.id ?? null

  return (
    <AdminLayout title="매칭 모니터">
      <div className="space-y-4">
        {runs.isError && <p className="text-sm text-red-700">{errorMessage(runs.error)}</p>}
        <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-gray-100 text-left text-xs text-gray-600">
              <tr>
                <th className="p-2">실행</th>
                <th className="p-2">시각</th>
                <th className="p-2">트리거</th>
                <th className="p-2">대상 회차</th>
                <th className="p-2">알고리즘</th>
                <th className="p-2">결과 요약</th>
              </tr>
            </thead>
            <tbody>
              {runs.data?.map((r) => (
                <tr
                  key={r.id}
                  onClick={() => setSelectedId(r.id)}
                  className={`cursor-pointer border-t border-gray-100 hover:bg-gray-50 ${r.id === selectedRunId ? 'bg-amber-50' : ''}`}
                >
                  <td className="p-2">#{r.id}</td>
                  <td className="p-2 whitespace-nowrap">{formatDateTime(r.startedAt)}</td>
                  <td className="p-2 whitespace-nowrap">{triggerOf(r)}</td>
                  <td className="p-2">{slotIdsOf(r).map(slotLabel).join(', ')}</td>
                  <td className="p-2">{r.algorithmVersion}</td>
                  <td className="p-2">{summaryText(r)}</td>
                </tr>
              ))}
              {runs.data?.length === 0 && (
                <tr>
                  <td colSpan={6} className="p-6 text-center text-gray-500">
                    아직 매칭 실행 기록이 없어요.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        {selectedRunId != null && <RunDetail runId={selectedRunId} slotLabel={slotLabel} />}
      </div>
    </AdminLayout>
  )
}

function slotIdsOf(r: MatchRunItem): number[] {
  const ids = r.summary.slotIds ?? r.params.slotIds
  return Array.isArray(ids) ? (ids as number[]) : []
}

function triggerOf(r: MatchRunItem): string {
  const t = r.params.trigger
  if (t === 'OPERATOR_BUTTON') return '운영자 실행'
  if (t === 'SCHEDULER') return '스케줄러(자동)'
  return String(t ?? '-')
}

function summaryText(r: MatchRunItem): string {
  const parts = SUMMARY_LABELS.filter(([k]) => typeof r.summary[k] === 'number' && (r.summary[k] as number) > 0).map(
    ([k, l]) => `${l} ${r.summary[k]}`,
  )
  return parts.length ? parts.join(' · ') : '-'
}

function RunDetail({
  runId,
  slotLabel,
}: {
  runId: number
  slotLabel: (id: number | null | undefined) => string
}) {
  const detail = useQuery({ queryKey: ['admin-match-run', runId], queryFn: () => api.matchRun(runId) })
  const [onlyNotable, setOnlyNotable] = useState(true)
  if (detail.isError) return <p className="text-sm text-red-700">{errorMessage(detail.error)}</p>
  if (!detail.data) return <p className="text-sm text-gray-500">불러오는 중…</p>
  const { run, tables, placementLogs } = detail.data

  const notable = (l: PlacementLog) => l.decision !== 'PLACED' && l.decision !== 'CONFIRMED'
  // 재배치 확정 로그(CONFIRMED)도 사유가 있는 사람은 같이 보이게, 사유 없는 일반 확정은 접는다
  const shown = onlyNotable ? placementLogs.filter((l) => notable(l) || l.reallocStep != null) : placementLogs

  return (
    <section className="space-y-4 rounded-lg border border-gray-200 bg-white p-4">
      <h2 className="font-semibold">
        실행 #{run.id} 상세 · {formatDateTime(run.startedAt)} · {triggerOf(run)}
      </h2>
      <div className="flex flex-wrap gap-2 text-xs">
        {SUMMARY_LABELS.map(([k, l]) => (
          <span key={k} className="rounded-full bg-gray-100 px-2 py-1">
            {l} {String(k === 'candidateCount' ? run.candidateCount : (run.summary[k] ?? 0))}
          </span>
        ))}
      </div>

      <div>
        <h3 className="mb-1 text-sm font-medium">테이블 ({tables.length})</h3>
        <div className="flex flex-wrap gap-2 text-sm">
          {tables.map((t) => (
            <Link
              key={t.id}
              to={`/admin/tables/${t.id}`}
              className={`rounded border px-3 py-2 ${t.venueAssigned ? 'border-gray-200' : 'border-red-300 bg-red-50'}`}
            >
              <span className="font-medium">테이블 #{t.id}</span> · {slotLabel(t.slotId)} · {t.memberCount}명 ·{' '}
              {TABLE_STATUS[t.tableStatus] ?? t.tableStatus}
              {!t.venueAssigned && <span className="ml-1 text-red-700">식당 없음</span>}
            </Link>
          ))}
        </div>
      </div>

      <div>
        <div className="mb-1 flex items-center justify-between">
          <h3 className="text-sm font-medium">배치 로그 ({shown.length}/{placementLogs.length})</h3>
          <label className="flex items-center gap-1 text-xs">
            <input type="checkbox" checked={onlyNotable} onChange={(e) => setOnlyNotable(e.target.checked)} />
            재배치·보류·제외·실패만 보기
          </label>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-gray-100 text-left text-xs text-gray-600">
              <tr>
                <th className="p-2">참가자</th>
                <th className="p-2">결정</th>
                <th className="p-2">회차 이동</th>
                <th className="p-2">재배치 단계</th>
                <th className="p-2">테이블</th>
                <th className="p-2">사유 코드 / 원인</th>
                <th className="p-2">사유</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((l) => (
                <tr key={l.id} className={`border-t border-gray-100 ${notable(l) ? 'bg-amber-50' : ''}`}>
                  <td className="p-2 whitespace-nowrap">
                    {l.userName ?? '-'} <span className="text-xs text-gray-500">{l.loginId}</span>
                  </td>
                  <td className="p-2 whitespace-nowrap font-medium">{DECISION[l.decision] ?? l.decision}</td>
                  <td className="p-2 whitespace-nowrap">
                    {l.fromSlotId || l.toSlotId ? `${slotLabel(l.fromSlotId)} → ${slotLabel(l.toSlotId)}` : '-'}
                  </td>
                  <td className="p-2">{l.reallocStep ?? '-'}</td>
                  <td className="p-2">
                    {l.toTableId ? (
                      <Link to={`/admin/tables/${l.toTableId}`} className="underline">
                        #{l.toTableId}
                      </Link>
                    ) : (
                      '-'
                    )}
                  </td>
                  <td className="p-2 text-xs">
                    {l.reasonCode}
                    {l.causeCode && <span className="text-gray-500"> / {l.causeCode}</span>}
                  </td>
                  <td className="p-2">{l.reasonText}</td>
                </tr>
              ))}
              {shown.length === 0 && (
                <tr>
                  <td colSpan={7} className="p-4 text-center text-gray-500">
                    표시할 로그가 없어요.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}
