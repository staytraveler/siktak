import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { CaseType, Rate } from '../../api/types'
import { CASE_TYPE, formatDateTime, percent } from '../../lib/format'

// 운영 대시보드 (/admin, PRD 11장·3장): 처리할 예외 · 전체 현황 · 자동화 지표 · 회차별 현황
const EXCEPTION_ORDER: CaseType[] = [
  'MATCH_CONFLICT',
  'VENUE_UNAVAILABLE',
  'NOTIFICATION_FAILED',
  'REFUND_FAILED',
  'NO_SHOW_CANDIDATE',
  'SAFETY_REPORT',
  'PAYMENT_ERROR',
  'DATA_MISSING',
]

export default function AdminHomePage() {
  const dashboard = useQuery({ queryKey: ['admin-dashboard'], queryFn: api.dashboard, refetchInterval: 15000 })
  const d = dashboard.data

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">운영 대시보드</h1>
      {dashboard.isError && <p className="text-sm text-red-700">{errorMessage(dashboard.error)}</p>}
      {d && (
        <>
          <section>
            <h2 className="mb-2 font-semibold">
              처리할 예외 <span className="text-sm font-normal text-gray-500">{d.exceptions.total}건</span>
            </h2>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              {EXCEPTION_ORDER.map((t) => {
                const n = d.exceptions.byType[t] ?? 0
                return (
                  <Link
                    key={t}
                    to={`/admin/exceptions?type=${t}`}
                    className={`rounded-lg border p-4 ${n > 0 ? 'border-red-300 bg-red-50' : 'border-gray-200 bg-white'}`}
                  >
                    <p className="text-xs text-gray-600">{CASE_TYPE[t]}</p>
                    <p className={`text-2xl font-bold ${n > 0 ? 'text-red-700' : 'text-gray-400'}`}>{n}</p>
                  </Link>
                )
              })}
            </div>
          </section>

          <section>
            <h2 className="mb-2 font-semibold">전체 현황</h2>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-6">
              <Stat label="신청자" value={d.totals.applicants} />
              <Stat label="신청 건" value={d.totals.applications} />
              <Stat label="결제 완료" value={d.totals.paid} />
              <Stat label="매칭 확정" value={d.totals.confirmed} />
              <Stat label="선택 대기(대체/환불)" value={d.totals.waitingChoice} />
              <Stat label="환불 완료" value={d.totals.refunded} />
            </div>
          </section>

          <section>
            <h2 className="mb-2 font-semibold">자동화 지표</h2>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
              <Metric label="매칭 성공률" hint="결제 완료 중 확정" rate={d.metrics.matchSuccess} />
              <Metric label="자동 확정률" hint="확정 테이블 중 예외 없이 확정" rate={d.metrics.autoConfirm} />
              <Metric label="자동 재배치 성공률" hint="최초 조합 실패자 중 재배치 성공" rate={d.metrics.autoRealloc} />
              <Metric label="자동 환불 완료율" hint="환불 선택 중 운영자 개입 없이 완료" rate={d.metrics.autoRefund} />
              <Metric label="운영자 개입률" hint="전체 신청 중 운영자가 처리한 예외" rate={d.metrics.operatorIntervention} />
              <Metric label="예외 발생률" hint="전체 신청 중 예외 건 (처리 여부 무관)" rate={d.metrics.exceptionRate} />
            </div>
          </section>

          <section>
            <h2 className="mb-2 font-semibold">회차별 현황</h2>
            <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
              <table className="w-full text-sm">
                <thead className="bg-gray-100 text-left text-xs text-gray-600">
                  <tr>
                    <th className="p-2">회차</th>
                    <th className="p-2">행사 시작</th>
                    <th className="p-2">상태</th>
                    <th className="p-2 text-right">신청</th>
                    <th className="p-2 text-right">결제 완료</th>
                    <th className="p-2 text-right">확정</th>
                    <th className="p-2 text-right">대체 대기</th>
                    <th className="p-2 text-right">환불</th>
                  </tr>
                </thead>
                <tbody>
                  {d.slots.map((s) => (
                    <tr key={s.slotId} className="border-t border-gray-100">
                      <td className="p-2">
                        #{s.slotId} {s.regionName}
                      </td>
                      <td className="p-2">{formatDateTime(s.startsAt)}</td>
                      <td className="p-2">{s.slotStatus}</td>
                      <td className="p-2 text-right">{s.applied}</td>
                      <td className="p-2 text-right">{s.paid}</td>
                      <td className="p-2 text-right">{s.confirmed}</td>
                      <td className="p-2 text-right">{s.waiting}</td>
                      <td className="p-2 text-right">{s.refunds}</td>
                    </tr>
                  ))}
                  {d.slots.length === 0 && (
                    <tr>
                      <td colSpan={8} className="p-4 text-center text-gray-500">
                        회차가 없어요.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <p className="mt-1 text-xs text-gray-500">
              신청·결제·대체 대기·환불은 후보로 고른 회차 기준, 확정은 배정된 회차 기준입니다.
            </p>
          </section>
        </>
      )}
    </div>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-3">
      <p className="text-xs text-gray-600">{label}</p>
      <p className="text-xl font-bold">{value}</p>
    </div>
  )
}

function Metric({ label, hint, rate }: { label: string; hint: string; rate: Rate }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-3">
      <p className="text-xs text-gray-600">{label}</p>
      <p className="text-xl font-bold">
        {percent(rate.rate)}{' '}
        <span className="text-xs font-normal text-gray-500">
          ({rate.numerator}/{rate.denominator})
        </span>
      </p>
      <p className="text-xs text-gray-500">{hint}</p>
    </div>
  )
}
