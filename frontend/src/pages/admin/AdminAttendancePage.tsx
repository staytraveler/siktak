import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import AdminLayout from '../../components/AdminLayout'
import { formatDateTime } from '../../lib/format'

const ATTENDANCE: Record<string, string> = {
  SCHEDULED: '참석 예정',
  ATTENDED: '참석 완료',
  CANCELED_EARLY: '사전 취소',
  CANCELED_LATE: '당일·직전 취소',
  NO_SHOW: '노쇼',
}

// 참석 관리 (PRD 11장): 체크인·사전 취소·당일 취소·노쇼 후보를 회차별로 보고, 노쇼 후보를 확정·해제한다 (FDB-01).
export default function AdminAttendancePage() {
  const queryClient = useQueryClient()
  const slots = useQuery({ queryKey: ['admin-slots'], queryFn: api.adminSlots })
  const [slotId, setSlotId] = useState<number | undefined>()
  const selected = slotId ?? slots.data?.[0]?.id
  const rows = useQuery({
    queryKey: ['admin-attendances', selected],
    queryFn: () => api.adminAttendances(selected as number),
    enabled: selected != null,
  })
  const [reason, setReason] = useState<Record<number, string>>({})
  const act = useMutation({
    mutationFn: ({ id, kind }: { id: number; kind: 'confirm' | 'dismiss' }) => {
      const text = (reason[id] ?? '').trim()
      return kind === 'confirm' ? api.confirmNoShowById(id, text) : api.dismissNoShowById(id, text)
    },
    onSuccess: () => queryClient.invalidateQueries(),
  })
  const th = 'whitespace-nowrap px-2 py-1.5 text-left font-medium'
  const td = 'whitespace-nowrap px-2 py-1.5'
  const count = (status: string) => rows.data?.filter((r) => r.attendanceStatus === status).length ?? 0

  return (
    <AdminLayout title="참석 관리">
      <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
        <select
          className="rounded border border-gray-300 bg-white px-2 py-1"
          value={selected ?? ''}
          onChange={(e) => setSlotId(Number(e.target.value))}
        >
          {slots.data?.map((s) => (
            <option key={s.id} value={s.id}>
              #{s.id} {s.regionName} · {formatDateTime(s.startsAt)}
            </option>
          ))}
        </select>
        {rows.data && (
          <span className="text-gray-600">
            참석 예정 {count('SCHEDULED')} · 참석 {count('ATTENDED')} · 사전 취소 {count('CANCELED_EARLY')} · 당일 취소{' '}
            {count('CANCELED_LATE')} · 노쇼 {count('NO_SHOW')}
          </span>
        )}
      </div>
      {(rows.isError || act.isError) && (
        <p className="mt-2 text-sm text-red-700">{errorMessage(rows.error ?? act.error)}</p>
      )}
      <div className="mt-3 overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-100">
            <tr>
              {['신청', '이름', '테이블', '참석 상태', '체크인', '노쇼 후보', '처리'].map((h) => (
                <th key={h} className={th}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {rows.data?.length === 0 && (
              <tr>
                <td className="p-3 text-gray-500" colSpan={7}>
                  이 회차에는 확정된 참석 정보가 없어요.
                </td>
              </tr>
            )}
            {rows.data?.map((r) => (
              <tr key={r.id} className={r.noShowCandidate ? 'bg-amber-50' : ''}>
                <td className={td}>#{r.applicationId}</td>
                <td className={td}>
                  {r.name} <span className="text-xs text-gray-500">{r.loginId}</span>
                </td>
                <td className={td}>#{r.diningTableId}</td>
                <td className={td}>{ATTENDANCE[r.attendanceStatus] ?? r.attendanceStatus}</td>
                <td className={td}>{r.checkedInAt ? formatDateTime(r.checkedInAt) : '-'}</td>
                <td className={td}>{r.noShowCandidate ? '후보' : '-'}</td>
                <td className={td}>
                  {r.noShowCandidate && r.attendanceStatus === 'SCHEDULED' ? (
                    <span className="flex items-center gap-1">
                      <input
                        className="w-40 rounded border border-gray-300 px-2 py-1"
                        placeholder="사유 (필수)"
                        value={reason[r.id] ?? ''}
                        onChange={(e) => setReason((p) => ({ ...p, [r.id]: e.target.value }))}
                      />
                      <button
                        type="button"
                        disabled={act.isPending || !(reason[r.id] ?? '').trim()}
                        className="rounded border border-red-300 bg-white px-2 py-1 text-red-700 disabled:opacity-40"
                        onClick={() => act.mutate({ id: r.id, kind: 'confirm' })}
                      >
                        노쇼 확정
                      </button>
                      <button
                        type="button"
                        disabled={act.isPending || !(reason[r.id] ?? '').trim()}
                        className="rounded border border-gray-300 bg-white px-2 py-1 disabled:opacity-40"
                        onClick={() => act.mutate({ id: r.id, kind: 'dismiss' })}
                      >
                        후보 해제
                      </button>
                    </span>
                  ) : (
                    '-'
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </AdminLayout>
  )
}
