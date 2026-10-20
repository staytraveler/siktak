import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import AdminLayout from '../../components/AdminLayout'
import { SEND_STATUS, formatDateTime } from '../../lib/format'

const TEMPLATE_NAME: Record<string, string> = {
  APPLICATION_SUBMITTED: '신청 완료',
  PAYMENT_PENDING: '결제 대기',
  PAYMENT_PAID: '결제 완료',
  REALLOCATING: '자동 재배치 중',
  MATCH_CONFIRMED: '매칭 확정',
  MATCH_FAILED: '매칭 실패',
  TRANSFERRED: '일정 이동 완료',
  REFUND_REQUESTED: '환불 요청',
  REFUND_COMPLETED: '환불 완료',
  REMINDER_D1: '행사 1일 전 리마인드',
  FEEDBACK_REQUEST: '행사 종료 후 피드백 요청',
  NEXT_SLOT_RECOMMEND: '다음 모집 추천',
  APPLICATION_CANCELED: '취소 접수',
}

// 자동 메시지 관리 (PRD 11장): 알림 템플릿(제목·본문·사용 여부)과 자동 발송 이력(인앱·푸시, 재시도 횟수).
export default function AdminMessagesPage() {
  const queryClient = useQueryClient()
  const templates = useQuery({ queryKey: ['admin-templates'], queryFn: api.adminTemplates })
  const [status, setStatus] = useState('')
  const history = useQuery({ queryKey: ['admin-message-history', status], queryFn: () => api.adminMessageHistory(status) })
  const [draft, setDraft] = useState<Record<string, { title: string; body: string; active: boolean }>>({})
  const save = useMutation({
    mutationFn: (code: string) => {
      const d = draft[code]
      return api.updateTemplate(code, d.title, d.body, d.active)
    },
    onSuccess: (_data, code) => {
      setDraft((p) => {
        const { [code]: _removed, ...rest } = p
        return rest
      })
      queryClient.invalidateQueries({ queryKey: ['admin-templates'] })
    },
  })
  const th = 'whitespace-nowrap px-2 py-1.5 text-left font-medium'
  const td = 'whitespace-nowrap px-2 py-1.5'

  return (
    <AdminLayout title="자동 메시지 관리">
      <h2 className="mt-4 font-semibold">알림 템플릿</h2>
      <p className="mt-1 text-xs text-gray-500">
        {'{{변수}}'}는 발송할 때 값으로 바뀌어요. 끄면 해당 알림은 만들어지지 않고 흐름만 계속돼요.
      </p>
      {(templates.isError || save.isError) && (
        <p className="mt-2 text-sm text-red-700">{errorMessage(templates.error ?? save.error)}</p>
      )}
      <div className="mt-2 grid gap-3 md:grid-cols-2">
        {templates.data?.map((t) => {
          const d = draft[t.code] ?? { title: t.title, body: t.body, active: t.active }
          const dirty = draft[t.code] != null
          return (
            <div key={t.code} className="rounded-lg border border-gray-200 bg-white p-3 text-sm">
              <div className="flex items-center justify-between">
                <b>{TEMPLATE_NAME[t.code] ?? t.code}</b>
                <label className="flex items-center gap-1 text-xs text-gray-600">
                  <input
                    type="checkbox"
                    checked={d.active}
                    onChange={(e) => setDraft((p) => ({ ...p, [t.code]: { ...d, active: e.target.checked } }))}
                  />
                  사용
                </label>
              </div>
              <span className="text-[11px] text-gray-400">{t.code}</span>
              <input
                className="mt-2 w-full rounded border border-gray-300 px-2 py-1"
                value={d.title}
                onChange={(e) => setDraft((p) => ({ ...p, [t.code]: { ...d, title: e.target.value } }))}
              />
              <textarea
                className="mt-1 w-full rounded border border-gray-300 px-2 py-1"
                rows={3}
                value={d.body}
                onChange={(e) => setDraft((p) => ({ ...p, [t.code]: { ...d, body: e.target.value } }))}
              />
              {dirty && (
                <div className="mt-1 flex gap-2">
                  <button
                    type="button"
                    disabled={save.isPending || !d.title.trim() || !d.body.trim()}
                    className="rounded bg-gray-900 px-3 py-1 text-white disabled:opacity-40"
                    onClick={() => save.mutate(t.code)}
                  >
                    저장
                  </button>
                  <button
                    type="button"
                    className="rounded border border-gray-300 px-3 py-1"
                    onClick={() =>
                      setDraft((p) => {
                        const { [t.code]: _removed, ...rest } = p
                        return rest
                      })
                    }
                  >
                    되돌리기
                  </button>
                </div>
              )}
            </div>
          )
        })}
      </div>

      <div className="mt-6 flex flex-wrap items-center gap-2 text-sm">
        <h2 className="font-semibold">자동 발송 이력</h2>
        <select className="rounded border border-gray-300 bg-white px-2 py-1" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">전체</option>
          {Object.entries(SEND_STATUS).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
        <span className="text-gray-500">{history.data ? `${history.data.length}건 (최근 300건)` : ''}</span>
      </div>
      <div className="mt-2 overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-100">
            <tr>
              {['#', '대상', '알림', '채널', '발송 상태', '푸시', '시도', '예약/발송 시각', '오류'].map((h) => (
                <th key={h} className={th}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {history.data?.map((n) => (
              <tr key={n.id} className={n.sendStatus === 'FAILED' ? 'bg-red-50' : ''}>
                <td className={td}>{n.id}</td>
                <td className={td}>
                  {n.name} <span className="text-xs text-gray-500">{n.loginId}</span>
                </td>
                <td className={td}>{TEMPLATE_NAME[n.templateCode] ?? n.templateCode}</td>
                <td className={td}>{n.channel === 'IN_APP' ? '앱 내' : n.channel}</td>
                <td className={td}>{SEND_STATUS[n.sendStatus] ?? n.sendStatus}</td>
                <td className={td}>{n.pushStatus === 'SENT' ? '발송' : n.pushStatus === 'FAILED' ? '실패' : '-'}</td>
                <td className={td}>{n.attempts}</td>
                <td className={td}>{formatDateTime(n.sentAt ?? n.scheduledAt)}</td>
                <td className={`${td} text-xs text-red-700`}>{n.lastError ?? ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </AdminLayout>
  )
}
