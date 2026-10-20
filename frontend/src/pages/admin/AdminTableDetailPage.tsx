import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import AdminLayout from '../../components/AdminLayout'
import { formatDateTime, SEND_STATUS, TABLE_STATUS } from '../../lib/format'

interface ScoreItem {
  code: string
  label: string
  raw: number
  weight: number
  weighted: number
}

interface SummaryShape {
  age?: { minBirthYear: number; maxBirthYear: number; gap: number }
  gender?: { male: number; female: number }
  mbti?: Record<string, number>
  commonInterests?: string[]
  conflicts?: boolean
  previousEncounters?: unknown[]
  slotMatch?: { choiceRank: number }[]
  preferredTraitMatch?: { satisfied: boolean }[]
}

// 테이블 상세 (/admin/tables/:id): 구성원 · 점수(S1~S6) · PRD 7.2 요약 · 식당 · 단체방 메시지 · 알림 발송 상태
export default function AdminTableDetailPage() {
  const id = Number(useParams().id)
  const table = useQuery({ queryKey: ['admin-table', id], queryFn: () => api.adminTable(id) })
  const messages = useQuery({ queryKey: ['admin-table-messages', id], queryFn: () => api.tableMessages(id) })
  const history = useQuery({ queryKey: ['admin-table-history', id], queryFn: () => api.tableHistory(id) })
  const notifications = useQuery({
    queryKey: ['admin-table-notifications', id],
    queryFn: () => api.tableNotifications(id),
    refetchInterval: 15000,
  })

  const queryClient = useQueryClient()
  const slotTables = useQuery({
    queryKey: ['admin-slot-tables', table.data?.slotId],
    queryFn: () => api.slotTables(table.data!.slotId),
    enabled: !!table.data,
  })
  const [moveApp, setMoveApp] = useState<number | ''>('')
  const [moveTo, setMoveTo] = useState<number | ''>('')
  const [reason, setReason] = useState('')
  const edit = useMutation({
    mutationFn: (kind: 'move' | 'dissolve') =>
      kind === 'move'
        ? api.moveTableMember(id, Number(moveApp), Number(moveTo), reason.trim())
        : api.dissolveTable(id, reason.trim()),
    onSuccess: () => {
      setReason('')
      setMoveApp('')
      setMoveTo('')
      queryClient.invalidateQueries()
    },
  })

  if (table.isError) return <p className="text-sm text-red-700">{errorMessage(table.error)}</p>
  const t = table.data
  if (!t) return <p className="text-sm text-gray-500">불러오는 중…</p>

  const items = ((t.scoreDetail as { items?: ScoreItem[] }).items ?? []) as ScoreItem[]
  const s = t.summary as SummaryShape
  const preferred = s.preferredTraitMatch ?? []

  return (
    <AdminLayout title="테이블 상세">
      <div className="space-y-5">
        <div>
          <Link to="/admin/matching" className="text-sm text-gray-500">
            ← 매칭 모니터
          </Link>
          <h1 className="text-2xl font-bold">
            테이블 #{t.id}{' '}
            <span className="text-base font-normal text-gray-500">
              회차 #{t.slotId} · {t.memberCount}명 · {TABLE_STATUS[t.tableStatus] ?? t.tableStatus} · 확정{' '}
              {formatDateTime(t.confirmedAt)}
            </span>
          </h1>
        </div>

        <Card title="구성원">
          <table className="w-full text-sm">
            <thead className="text-left text-xs text-gray-600">
              <tr>
                <th className="p-1">이름</th>
                <th className="p-1">출생연도</th>
                <th className="p-1">성별</th>
                <th className="p-1">MBTI</th>
                <th className="p-1">관심사</th>
              </tr>
            </thead>
            <tbody>
              {t.members.map((m) => (
                <tr key={m.userId} className="border-t border-gray-100">
                  <td className="p-1">
                    {m.name ?? '-'} <span className="text-xs text-gray-500">{m.loginId}</span>
                  </td>
                  <td className="p-1">{m.birthYear ? `${m.birthYear}년생` : '-'}</td>
                  <td className="p-1">{m.gender === 'MALE' ? '남' : m.gender === 'FEMALE' ? '여' : (m.gender ?? '-')}</td>
                  <td className="p-1">{m.mbti ?? '-'}</td>
                  <td className="p-1">{m.interests.join(', ') || '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>

        <div className="grid gap-5 lg:grid-cols-2">
          <Card title={`점수 ${t.totalScore != null ? `(합계 ${Number(t.totalScore).toFixed(1)})` : ''}`}>
            {items.length === 0 && <p className="text-sm text-gray-500">점수 정보가 없어요.</p>}
            <ul className="space-y-1 text-sm">
              {items.map((i) => (
                <li key={i.code} className="flex items-center gap-2">
                  <span className="w-44 shrink-0">
                    {i.code} {i.label}
                  </span>
                  <span className="h-2 flex-1 rounded bg-gray-100">
                    <span className="block h-2 rounded bg-gray-700" style={{ width: `${Math.round(i.raw * 100)}%` }} />
                  </span>
                  <span className="w-24 text-right text-xs text-gray-600">
                    {i.weighted.toFixed(1)} / {i.weight.toFixed(0)}
                  </span>
                </li>
              ))}
            </ul>
          </Card>

          <Card title="매칭 요약 (PRD 7.2)">
            <dl className="grid grid-cols-[7rem_1fr] gap-y-1 text-sm">
              <dt className="text-gray-600">연령대</dt>
              <dd>{s.age ? `${s.age.minBirthYear}~${s.age.maxBirthYear}년생 (차이 ${s.age.gap}살)` : '-'}</dd>
              <dt className="text-gray-600">성비</dt>
              <dd>{s.gender ? `남 ${s.gender.male} · 여 ${s.gender.female}` : '-'}</dd>
              <dt className="text-gray-600">MBTI</dt>
              <dd>
                {s.mbti
                  ? `E ${s.mbti.e ?? 0}/I ${s.mbti.i ?? 0} · N ${s.mbti.n ?? 0}/S ${s.mbti.s ?? 0} · T ${s.mbti.t ?? 0}/F ${s.mbti.f ?? 0} · J ${s.mbti.j ?? 0}/P ${s.mbti.p ?? 0}`
                  : '-'}
              </dd>
              <dt className="text-gray-600">공통 관심사</dt>
              <dd>{s.commonInterests?.join(', ') || '-'}</dd>
              <dt className="text-gray-600">희망 성향 충족</dt>
              <dd>
                {preferred.filter((p) => p.satisfied).length}/{preferred.length}명
              </dd>
              <dt className="text-gray-600">선택 회차 순위</dt>
              <dd>
                {(s.slotMatch ?? []).filter((x) => x.choiceRank === 1).length}명 1순위 ·{' '}
                {(s.slotMatch ?? []).filter((x) => x.choiceRank > 1).length}명 그 외
              </dd>
              <dt className="text-gray-600">이전 만남</dt>
              <dd>{s.previousEncounters?.length ?? 0}쌍</dd>
              <dt className="text-gray-600">조건 충돌</dt>
              <dd>{s.conflicts ? '있음' : '없음'}</dd>
            </dl>
          </Card>
        </div>

        <Card title="식당 · 예약">
          {t.venue ? (
            <p className="text-sm">
              {t.venue.name} · {t.venue.address} · 예약명 <b>{t.reservationName ?? '-'}</b> · 배정{' '}
              {formatDateTime(t.venueAssignedAt)}
            </p>
          ) : (
            <p className="text-sm text-red-700">
              식당이 배정되지 않았어요 (예약명 {t.reservationName ?? '-'}).{' '}
              <Link to="/admin/exceptions?type=VENUE_UNAVAILABLE" className="underline">
                예외 처리함에서 수용량을 늘려 재배정
              </Link>
            </p>
          )}
        </Card>

        {t.tableStatus === 'CONFIRMED' && (
          <Card title="운영자 긴급 수정 (확정 후 취소·안전 문제 등 예외 상황에서만)">
            <p className="text-xs text-gray-500">
              정상 흐름에서는 쓰지 않아요. 옮긴 뒤에도 필수 조건(최대 6명·연령 차 8살·차단·피하고 싶음)을 다시 검증하고, 모든 변경은
              사유와 함께 예외 처리함에 남아요.
            </p>
            <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
              <select className="rounded border border-gray-300 bg-white px-2 py-1" value={moveApp} onChange={(e) => setMoveApp(e.target.value ? Number(e.target.value) : '')}>
                <option value="">옮길 참가자</option>
                {t.members.map((m) => (
                  <option key={m.applicationId} value={m.applicationId}>
                    {m.name ?? '#' + m.userId}
                  </option>
                ))}
              </select>
              <span>→</span>
              <select className="rounded border border-gray-300 bg-white px-2 py-1" value={moveTo} onChange={(e) => setMoveTo(e.target.value ? Number(e.target.value) : '')}>
                <option value="">옮길 테이블</option>
                {slotTables.data
                  ?.filter((x) => x.id !== t.id && x.tableStatus === 'CONFIRMED' && x.memberCount < 6)
                  .map((x) => (
                    <option key={x.id} value={x.id}>
                      테이블 #{x.id} ({x.memberCount}명)
                    </option>
                  ))}
              </select>
              <input
                className="min-w-[200px] flex-1 rounded border border-gray-300 px-2 py-1"
                placeholder="사유 (필수)"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
              <button
                type="button"
                disabled={edit.isPending || !reason.trim() || moveApp === '' || moveTo === ''}
                className="rounded border border-gray-300 bg-white px-3 py-1 disabled:opacity-40"
                onClick={() => edit.mutate('move')}
              >
                참가자 이동
              </button>
              <button
                type="button"
                disabled={edit.isPending || !reason.trim()}
                className="rounded border border-red-300 bg-white px-3 py-1 text-red-700 disabled:opacity-40"
                onClick={() => window.confirm('이 테이블을 해제할까요? 구성원은 대체 일정 선택으로 넘어가고 단체방은 닫혀요.') && edit.mutate('dissolve')}
              >
                테이블 해제
              </button>
            </div>
            {edit.isError && <p className="mt-2 text-sm text-red-700">{errorMessage(edit.error)}</p>}
          </Card>
        )}

        <Card title="상태 이력">
          {history.isError && <p className="text-sm text-red-700">{errorMessage(history.error)}</p>}
          {history.data && history.data.length === 0 && <p className="text-sm text-gray-500">이력이 없어요.</p>}
          {history.data && history.data.length > 0 && !history.data.some((h) => h.actorType === 'OPERATOR') && (
            <p className="mb-2 text-sm text-emerald-700">운영자 개입 없이 처리됐어요 (주체: 시스템·스케줄러).</p>
          )}
          <ol className="max-h-96 space-y-1 overflow-y-auto border-l-2 border-gray-200 pl-3 text-sm">
            {history.data?.map((h, i) => (
              <li key={i}>
                <span className="text-xs text-gray-500">{formatDateTime(h.at)}</span> <b>{h.target}</b> ·{' '}
                {h.from ? `${h.from} → ` : ''}
                {h.to}{' '}
                <span className={`rounded px-1 text-xs ${h.actorType === 'OPERATOR' ? 'bg-amber-100' : 'bg-gray-100'}`}>
                  {h.actorType}
                </span>
                {h.reason && <span className="text-xs text-gray-600"> · {h.reason}</span>}
              </li>
            ))}
          </ol>
        </Card>

        <div className="grid gap-5 lg:grid-cols-2">
          <Card title={`단체방 메시지 (${messages.data?.length ?? 0})`}>
            {messages.isError && <p className="text-sm text-red-700">{errorMessage(messages.error)}</p>}
            {t.chatRoomId == null && <p className="text-sm text-gray-500">단체방이 아직 없어요.</p>}
            <ul className="max-h-96 space-y-2 overflow-y-auto text-sm">
              {messages.data?.map((m) => (
                <li key={m.id} className="rounded bg-gray-50 p-2">
                  <p className="text-xs text-gray-500">
                    {m.messageType} · {formatDateTime(m.createdAt)}
                  </p>
                  <p className="whitespace-pre-wrap">{m.body}</p>
                </li>
              ))}
            </ul>
          </Card>

          <Card title="알림 발송 상태">
            {notifications.isError && <p className="text-sm text-red-700">{errorMessage(notifications.error)}</p>}
            <div className="max-h-96 overflow-y-auto">
              <table className="w-full text-sm">
                <thead className="text-left text-xs text-gray-600">
                  <tr>
                    <th className="p-1">대상</th>
                    <th className="p-1">알림</th>
                    <th className="p-1">상태</th>
                    <th className="p-1">푸시</th>
                    <th className="p-1">시각</th>
                  </tr>
                </thead>
                <tbody>
                  {notifications.data?.map((n) => (
                    <tr key={n.id} className="border-t border-gray-100">
                      <td className="p-1">{n.userName ?? n.userId}</td>
                      <td className="p-1">{n.templateCode}</td>
                      <td className={`p-1 ${n.sendStatus === 'FAILED' ? 'font-medium text-red-700' : ''}`}>
                        {SEND_STATUS[n.sendStatus] ?? n.sendStatus}
                        {n.attempts > 1 && <span className="text-xs text-gray-500"> ({n.attempts}회)</span>}
                      </td>
                      <td className="p-1 text-xs">{n.pushStatus === 'SENT' ? '발송' : n.pushStatus === 'FAILED' ? '실패' : '-'}</td>
                      <td className="p-1 whitespace-nowrap text-xs">{formatDateTime(n.sentAt ?? n.scheduledAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </div>
    </AdminLayout>
  )
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-lg border border-gray-200 bg-white p-4">
      <h2 className="mb-2 font-semibold">{title}</h2>
      {children}
    </section>
  )
}
