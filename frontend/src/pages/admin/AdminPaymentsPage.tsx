import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import AdminLayout from '../../components/AdminLayout'
import { APPLICATION_STATUS, MATCH_STATUS, PAYMENT_STATUS, formatDateTime, label } from '../../lib/format'

const PASS_TYPE: Record<string, string> = {
  SINGLE: '1회권',
  CREDIT: '보관 이용권',
  MEMBERSHIP: '멤버십',
  SINGLE_CREDIT: '보관 1회권',
  MEMBERSHIP_1M: '멤버십 1개월',
  MEMBERSHIP_3M: '멤버십 3개월',
  MEMBERSHIP_6M: '멤버십 6개월',
}
const PASS_STATUS: Record<string, string> = { ACTIVE: '사용 가능', USED: '사용함', EXPIRED: '만료' }

// 결제·환불 (PRD 11장): 결제·자동 환불·환불 실패·이용권 사용 내역 (PAY-01·03, RET-03).
export default function AdminPaymentsPage() {
  const [status, setStatus] = useState('')
  const payments = useQuery({ queryKey: ['admin-payments', status], queryFn: () => api.adminPayments(status) })
  const passes = useQuery({ queryKey: ['admin-passes'], queryFn: api.adminPasses })
  const th = 'whitespace-nowrap px-2 py-1.5 text-left font-medium'
  const td = 'whitespace-nowrap px-2 py-1.5'
  const refundFailed = payments.data?.filter((p) => p.paymentStatus === 'REFUND_FAILED').length ?? 0

  return (
    <AdminLayout title="결제·환불">
      <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
        <select
          className="rounded border border-gray-300 bg-white px-2 py-1"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          <option value="">전체 결제 상태</option>
          {Object.entries(PAYMENT_STATUS).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
        <span className="text-gray-500">{payments.data ? `${payments.data.length}건` : ''}</span>
        {refundFailed > 0 && (
          <span className="rounded bg-red-50 px-2 py-1 font-medium text-red-700">
            환불 실패 {refundFailed}건 — 예외 처리함에서 재처리하세요
          </span>
        )}
      </div>
      {payments.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(payments.error)}</p>}
      <div className="mt-3 overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-100">
            <tr>
              {['신청', '이름', '이용권', '결제 상태', '금액', 'PG 거래', '신청 상태', '매칭', '신청 시각', '비고'].map((h) => (
                <th key={h} className={th}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {payments.data?.map((p) => (
              <tr key={p.applicationId} className={p.paymentStatus === 'REFUND_FAILED' ? 'bg-red-50' : ''}>
                <td className={td}>#{p.applicationId}</td>
                <td className={td}>
                  {p.name} <span className="text-xs text-gray-500">{p.loginId}</span>
                </td>
                <td className={td}>{PASS_TYPE[p.passType] ?? p.passType}</td>
                <td className={td}>{label(PAYMENT_STATUS, p.paymentStatus)}</td>
                <td className={td}>{p.amount != null ? `${p.amount.toLocaleString('ko-KR')}원` : '-'}</td>
                <td className={td}>{p.txStatus ?? '-'}</td>
                <td className={td}>{label(APPLICATION_STATUS, p.applicationStatus)}</td>
                <td className={td}>{label(MATCH_STATUS, p.matchStatus)}</td>
                <td className={td}>{formatDateTime(p.submittedAt)}</td>
                <td className={`${td} text-xs text-gray-500`}>
                  {p.userPassId ? `이용권 #${p.userPassId} 사용` : ''}
                  {p.lastError ? ` ${p.lastError}` : ''}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h2 className="mt-6 font-semibold">이용권 사용 내역 (멤버십·보관 크레딧)</h2>
      <div className="mt-2 overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-100">
            <tr>
              {['#', '이름', '종류', '상태', '시작', '만료', '발급 신청', '사용 신청'].map((h) => (
                <th key={h} className={th}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {passes.data?.length === 0 && (
              <tr>
                <td className="p-3 text-gray-500" colSpan={8}>
                  아직 이용권이 없어요.
                </td>
              </tr>
            )}
            {passes.data?.map((p) => (
              <tr key={p.id}>
                <td className={td}>{p.id}</td>
                <td className={td}>
                  {p.name} <span className="text-xs text-gray-500">{p.loginId}</span>
                </td>
                <td className={td}>{PASS_TYPE[p.passType] ?? p.passType}</td>
                <td className={td}>{PASS_STATUS[p.passStatus] ?? p.passStatus}</td>
                <td className={td}>{formatDateTime(p.startsAt)}</td>
                <td className={td}>{p.expiresAt ? formatDateTime(p.expiresAt) : '없음'}</td>
                <td className={td}>{p.sourceApplicationId ? `#${p.sourceApplicationId}` : '-'}</td>
                <td className={td}>{p.usedApplicationId ? `#${p.usedApplicationId}` : '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </AdminLayout>
  )
}
