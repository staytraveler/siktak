import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { AdminReport, ResolveAction } from '../../api/types'
import { formatDateTime } from '../../lib/format'

const ACTIONS: { value: ResolveAction; label: string }[] = [
  { value: 'DISMISS', label: '기각' },
  { value: 'WARNING', label: '경고' },
  { value: 'RESTRICTION', label: '참가 제한' },
  { value: 'PERMANENT_BAN', label: '영구 정지' },
]

// 안전 신고 처리 (SAF-02). 기능만, 디자인 없음
export default function AdminReportsPage() {
  const [onlyOpen, setOnlyOpen] = useState(true)
  const reports = useQuery({ queryKey: ['admin-reports', onlyOpen], queryFn: () => api.adminReports(onlyOpen) })

  return (
    <div className="max-w-4xl">
      <h1 className="text-2xl font-bold">안전 신고</h1>
      <label className="mt-4 flex items-center gap-2 text-sm">
        <input type="checkbox" checked={onlyOpen} onChange={(e) => setOnlyOpen(e.target.checked)} />
        미처리만 보기
      </label>
      {reports.data?.length === 0 && <p className="mt-4 text-sm text-gray-500">신고가 없어요.</p>}
      <ul className="mt-4 space-y-3">
        {reports.data?.map((r) => <ReportRow key={r.id} report={r} />)}
      </ul>
    </div>
  )
}

function ReportRow({ report }: { report: AdminReport }) {
  const queryClient = useQueryClient()
  const [action, setAction] = useState<ResolveAction>('WARNING')
  const [days, setDays] = useState('30')
  const [note, setNote] = useState('')
  const resolve = useMutation({
    mutationFn: () =>
      api.resolveReport(report.id, {
        action,
        days: action === 'RESTRICTION' ? Number(days) : undefined,
        note: note || undefined,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin-reports'] }),
  })
  const closed = report.reportStatus === 'RESOLVED' || report.reportStatus === 'DISMISSED'

  return (
    <li className="rounded-lg border border-gray-200 bg-white p-4 text-sm">
      <p className="font-medium">
        #{report.id} {report.reporterName} → {report.reportedName}{' '}
        <span className="text-xs text-gray-500">
          ({report.reasonType} · {report.reportStatus} · {formatDateTime(report.createdAt)})
        </span>
      </p>
      <p className="mt-1 whitespace-pre-wrap text-gray-700">{report.description}</p>
      {!closed && (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <select
            value={action}
            onChange={(e) => setAction(e.target.value as ResolveAction)}
            className="rounded border border-gray-300 px-2 py-1"
          >
            {ACTIONS.map((a) => (
              <option key={a.value} value={a.value}>
                {a.label}
              </option>
            ))}
          </select>
          {action === 'RESTRICTION' && (
            <input
              value={days}
              onChange={(e) => setDays(e.target.value)}
              className="w-16 rounded border border-gray-300 px-2 py-1"
              aria-label="제한 일수"
            />
          )}
          <input
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="메모"
            className="min-w-40 flex-1 rounded border border-gray-300 px-2 py-1"
          />
          <button
            type="button"
            disabled={resolve.isPending}
            onClick={() => resolve.mutate()}
            className="rounded bg-amber-800 px-3 py-1 text-white disabled:bg-gray-300"
          >
            처리
          </button>
        </div>
      )}
      {resolve.isError && <p className="mt-2 text-red-700">{errorMessage(resolve.error)}</p>}
    </li>
  )
}
