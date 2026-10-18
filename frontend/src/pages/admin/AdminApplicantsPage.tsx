import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { api, downloadCsv } from '../../api/endpoints'
import AdminLayout from '../../components/AdminLayout'
import { APPLICATION_STATUS, MATCH_STATUS, PAYMENT_STATUS, formatDateTime, label } from '../../lib/format'

const ATTENDANCE_STATUS: Record<string, string> = {
  SCHEDULED: '참석 예정',
  ATTENDED: '참석',
  NO_SHOW: '노쇼',
  CANCELED_EARLY: '사전 취소',
  CANCELED_LATE: '직전 취소',
}

// 신청자 목록 + CSV 내보내기 (PRD 11장 신청자 목록, 16장)
export default function AdminApplicantsPage() {
  const slots = useQuery({ queryKey: ['admin-slots'], queryFn: api.adminSlots })
  const [slotId, setSlotId] = useState<number | undefined>()
  const applicants = useQuery({ queryKey: ['admin-applicants', slotId], queryFn: () => api.applicants(slotId) })
  const [error, setError] = useState<string | null>(null)
  const download = (kind: 'applicants' | 'tables') => {
    setError(null)
    downloadCsv(kind, slotId).catch((e) => setError(errorMessage(e)))
  }
  const th = 'whitespace-nowrap px-2 py-1.5 text-left font-medium'
  const td = 'whitespace-nowrap px-2 py-1.5'

  return (
    <AdminLayout title="신청자 목록">
      <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
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
        <button type="button" className="rounded border bg-white px-3 py-1" onClick={() => download('applicants')}>
          신청자 CSV
        </button>
        <button type="button" className="rounded border bg-white px-3 py-1" onClick={() => download('tables')}>
          테이블 결과 CSV
        </button>
        <span className="text-gray-500">{applicants.data ? `${applicants.data.length}건` : ''}</span>
      </div>
      {(error || applicants.isError) && (
        <p className="mt-2 text-sm text-red-700">{error ?? errorMessage(applicants.error)}</p>
      )}
      <div className="mt-3 overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-100">
            <tr>
              {['#', '이름', '신청', '결제', '매칭', '참석', '배정/희망 회차', '프로필', '이전 참석', '신고 받음'].map((h) => (
                <th key={h} className={th}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {applicants.data?.map((a) => (
              <tr key={a.applicationId}>
                <td className={td}>{a.applicationId}</td>
                <td className={td}>
                  {a.name} <span className="text-xs text-gray-500">{a.loginId}</span>
                </td>
                <td className={td}>{label(APPLICATION_STATUS, a.applicationStatus)}</td>
                <td className={td}>{label(PAYMENT_STATUS, a.paymentStatus)}</td>
                <td className={td}>{label(MATCH_STATUS, a.matchStatus)}</td>
                <td className={td}>{label(ATTENDANCE_STATUS, a.attendanceStatus)}</td>
                <td className={td}>
                  {a.assignedSlotId ? `#${a.assignedSlotId}` : '-'}
                  {a.slotChoices && <span className="text-xs text-gray-500"> ({a.slotChoices})</span>}
                </td>
                <td className={td}>
                  {[a.birthYear, a.gender, a.mbti, a.job].filter(Boolean).join(' · ') || '-'}
                </td>
                <td className={td}>{a.attendedCount}</td>
                <td className={`${td} ${a.reportedCount > 0 ? 'font-semibold text-red-700' : ''}`}>{a.reportedCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </AdminLayout>
  )
}
