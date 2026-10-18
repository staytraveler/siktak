import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import AdminLayout from '../../components/AdminLayout'
import type { ExceptionCase } from '../../api/types'
import { CASE_STATUS, CASE_TYPE, formatDateTime } from '../../lib/format'

// 예외 처리함 (/admin/exceptions, PRD 11장): 자동 처리되지 않은 예외만 모아 유형별 조치를 연결한다.
export default function AdminExceptionsPage() {
  const [params, setParams] = useSearchParams()
  const typeFilter = params.get('type') ?? ''
  const statusFilter = params.get('status') ?? 'OPEN'
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const exceptions = useQuery({ queryKey: ['admin-exceptions'], queryFn: api.exceptions, refetchInterval: 15000 })

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
  }

  const all = exceptions.data ?? []
  const rows = all
    .filter((c) => !typeFilter || c.caseType === typeFilter)
    .filter((c) => {
      if (statusFilter === 'ALL') return true
      if (statusFilter === 'OPEN') return c.caseStatus !== 'RESOLVED'
      return c.caseStatus === statusFilter
    })
    .sort((a, b) => b.id - a.id)
  const selected = all.find((c) => c.id === selectedId) ?? null
  const select = 'rounded border border-gray-300 bg-white px-2 py-1 text-sm'

  return (
    <AdminLayout title="예외 처리함">
      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <label>
            유형{' '}
            <select className={select} value={typeFilter} onChange={(e) => setFilter('type', e.target.value)}>
              <option value="">전체</option>
              {Object.entries(CASE_TYPE).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label>
            상태{' '}
            <select className={select} value={statusFilter} onChange={(e) => setFilter('status', e.target.value)}>
              <option value="OPEN">처리할 것(대기·처리 중)</option>
              <option value="RESOLVED">해결됨</option>
              <option value="ALL">전체</option>
            </select>
          </label>
          <span className="text-gray-500">{rows.length}건</span>
        </div>
        {exceptions.isError && <p className="text-sm text-red-700">{errorMessage(exceptions.error)}</p>}

        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
          <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
            <table className="w-full text-sm">
              <thead className="bg-gray-100 text-left text-xs text-gray-600">
                <tr>
                  <th className="p-2">유형</th>
                  <th className="p-2">대상</th>
                  <th className="p-2">사유</th>
                  <th className="p-2">발생</th>
                  <th className="p-2">상태</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((c) => (
                  <tr
                    key={c.id}
                    onClick={() => setSelectedId(c.id)}
                    className={`cursor-pointer border-t border-gray-100 hover:bg-gray-50 ${c.id === selectedId ? 'bg-amber-50' : ''}`}
                  >
                    <td className="p-2 whitespace-nowrap">{CASE_TYPE[c.caseType] ?? c.caseType}</td>
                    <td className="p-2 whitespace-nowrap">{targetOf(c)}</td>
                    <td className="max-w-xs truncate p-2" title={c.reason}>
                      {c.reason}
                    </td>
                    <td className="p-2 whitespace-nowrap">{formatDateTime(c.createdAt)}</td>
                    <td className="p-2 whitespace-nowrap">{CASE_STATUS[c.caseStatus]}</td>
                  </tr>
                ))}
                {rows.length === 0 && (
                  <tr>
                    <td colSpan={5} className="p-6 text-center text-gray-500">
                      처리할 예외가 없어요.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div>{selected ? <CasePanel key={selected.id} c={selected} /> : <Empty />}</div>
        </div>
      </div>
    </AdminLayout>
  )
}

function Empty() {
  return (
    <div className="rounded-lg border border-dashed border-gray-300 p-6 text-center text-sm text-gray-500">
      왼쪽에서 예외를 선택하면 조치 화면이 열려요.
    </div>
  )
}

function targetOf(c: ExceptionCase): string {
  if (c.diningTableId) return `테이블 #${c.diningTableId}`
  if (c.applicationId) return `신청 #${c.applicationId}`
  return '-'
}

function CasePanel({ c }: { c: ExceptionCase }) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const done = () => {
    setReason('')
    void queryClient.invalidateQueries({ queryKey: ['admin-exceptions'] })
    void queryClient.invalidateQueries({ queryKey: ['admin-dashboard'] })
  }
  const act = useMutation({
    mutationFn: (v: { action: string; withReason: boolean }) =>
      api.exceptionAction(c.id, v.action, v.withReason ? reason.trim() : undefined),
    onSuccess: done,
  })
  const run = (action: string, withReason = true) => act.mutate({ action, withReason })
  const resolved = c.caseStatus === 'RESOLVED'
  const noReason = reason.trim() === ''
  const btn = 'rounded bg-gray-900 px-3 py-1.5 text-sm text-white disabled:opacity-40'
  const ghost = 'rounded border border-gray-300 bg-white px-3 py-1.5 text-sm disabled:opacity-40'

  return (
    <section className="space-y-3 rounded-lg border border-gray-200 bg-white p-4 text-sm">
      <div className="flex items-start justify-between gap-2">
        <h2 className="font-semibold">
          #{c.id} {CASE_TYPE[c.caseType] ?? c.caseType} · {targetOf(c)}
        </h2>
        <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs">{CASE_STATUS[c.caseStatus]}</span>
      </div>
      <p>{c.reason}</p>
      <p className="text-xs text-gray-500">발생 {formatDateTime(c.createdAt)}</p>
      {c.detail && (
        <pre className="overflow-x-auto rounded bg-gray-50 p-2 text-xs">{JSON.stringify(c.detail, null, 2)}</pre>
      )}

      {resolved ? (
        <div className="rounded bg-green-50 p-3 text-green-900">
          <p className="font-medium">해결됨</p>
          <p>처리자: {c.resolvedBy ? `운영자 #${c.resolvedBy}` : '자동 처리'}</p>
          <p>처리 시각: {formatDateTime(c.resolvedAt)}</p>
          <p>사유: {c.resolutionNote ?? '-'}</p>
        </div>
      ) : (
        <div className="space-y-3 border-t border-gray-100 pt-3">
          {c.caseType === 'VENUE_UNAVAILABLE' && <VenueFix c={c} />}
          {c.caseType === 'SAFETY_REPORT' && (
            <Link to="/admin/reports" className="inline-block text-amber-800 underline">
              신고 상세에서 조치하기 →
            </Link>
          )}
          {c.caseType === 'NOTIFICATION_FAILED' && (
            <button type="button" className={btn} disabled={act.isPending} onClick={() => run('resend-notification', false)}>
              알림 재발송
            </button>
          )}
          {c.caseType === 'REFUND_FAILED' && (
            <button type="button" className={ghost} disabled={act.isPending} onClick={() => run('retry-refund', false)}>
              환불 재처리
            </button>
          )}

          {c.caseType !== 'SAFETY_REPORT' && c.caseType !== 'NOTIFICATION_FAILED' && (
            <>
              <label className="block">
                <span className="text-xs text-gray-600">처리 사유 (필수)</span>
                <textarea
                  className="mt-1 w-full rounded border border-gray-300 p-2"
                  rows={2}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                />
              </label>
              <div className="flex flex-wrap gap-2">
                {c.caseType === 'REFUND_FAILED' && (
                  <button type="button" className={btn} disabled={act.isPending || noReason} onClick={() => run('mark-refunded')}>
                    수동 환불 완료 처리
                  </button>
                )}
                {c.caseType === 'NO_SHOW_CANDIDATE' && (
                  <>
                    <button type="button" className={btn} disabled={act.isPending || noReason} onClick={() => run('confirm-no-show')}>
                      노쇼 확정
                    </button>
                    <button type="button" className={ghost} disabled={act.isPending || noReason} onClick={() => run('dismiss-no-show')}>
                      후보 해제
                    </button>
                  </>
                )}
                {c.caseType !== 'NO_SHOW_CANDIDATE' && c.caseType !== 'REFUND_FAILED' && c.caseType !== 'VENUE_UNAVAILABLE' && (
                  <button type="button" className={btn} disabled={act.isPending || noReason} onClick={() => run('resolve')}>
                    직접 처리 완료
                  </button>
                )}
              </div>
            </>
          )}
          {act.isError && <p className="text-red-700">{errorMessage(act.error)}</p>}
        </div>
      )}
    </section>
  )
}

// 식당 없음: 수용량을 늘리고 같은 화면에서 재배정한다 (수용량 변경 → assign-venue).
function VenueFix({ c }: { c: ExceptionCase }) {
  const queryClient = useQueryClient()
  const slotId = Number(c.detail?.slotId)
  const regionId = Number(c.detail?.regionId)
  const venues = useQuery({ queryKey: ['admin-venues'], queryFn: api.adminVenues })
  const capacities = useQuery({
    queryKey: ['venue-capacities', slotId],
    queryFn: () => api.venueCapacities(slotId),
    enabled: Number.isFinite(slotId),
  })
  const [venueId, setVenueId] = useState<number | null>(null)
  const [maxTables, setMaxTables] = useState<number | null>(null)
  const [reason, setReason] = useState('')

  const list = (venues.data ?? [])
    .filter((x) => x.regionId === regionId && x.active)
    .map((x) => {
      const cap = capacities.data?.find((k) => k.venueId === x.id)
      return { venueId: x.id, venueName: x.name, maxTables: cap?.maxTables ?? 0, reservedTables: cap?.reservedTables ?? 0 }
    })
  const current = list.find((x) => x.venueId === (venueId ?? list[0]?.venueId))
  const target = maxTables ?? (current ? current.maxTables + 1 : 1)

  const fix = useMutation({
    mutationFn: async () => {
      if (!current) throw new Error('식당을 선택해 주세요.')
      await api.setVenueCapacity(current.venueId, slotId, target)
      await api.exceptionAction(c.id, 'assign-venue', reason.trim())
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin-exceptions'] })
      void queryClient.invalidateQueries({ queryKey: ['admin-dashboard'] })
      void queryClient.invalidateQueries({ queryKey: ['venue-capacities', slotId] })
    },
  })

  return (
    <div className="space-y-2">
      <p className="font-medium">수용량 늘리기 + 재배정</p>
      {(capacities.isError || venues.isError) && <p className="text-red-700">{errorMessage(capacities.error ?? venues.error)}</p>}
      <div className="flex flex-wrap items-center gap-2">
        <select
          className="rounded border border-gray-300 px-2 py-1"
          value={current?.venueId ?? ''}
          onChange={(e) => {
            setVenueId(Number(e.target.value))
            setMaxTables(null)
          }}
        >
          {list.map((v) => (
            <option key={v.venueId} value={v.venueId}>
              {v.venueName} (배정 {v.reservedTables}/{v.maxTables})
            </option>
          ))}
        </select>
        <label className="flex items-center gap-1">
          최대 테이블
          <input
            type="number"
            min={current?.reservedTables ?? 0}
            className="w-16 rounded border border-gray-300 px-2 py-1"
            value={target}
            onChange={(e) => setMaxTables(Number(e.target.value))}
          />
        </label>
      </div>
      <textarea
        className="w-full rounded border border-gray-300 p-2"
        rows={2}
        placeholder="처리 사유 (필수)"
        value={reason}
        onChange={(e) => setReason(e.target.value)}
      />
      <button
        type="button"
        className="rounded bg-gray-900 px-3 py-1.5 text-sm text-white disabled:opacity-40"
        disabled={fix.isPending || reason.trim() === '' || !current}
        onClick={() => fix.mutate()}
      >
        수용량 변경하고 재배정
      </button>
      {fix.isError && <p className="text-red-700">{errorMessage(fix.error)}</p>}
    </div>
  )
}
