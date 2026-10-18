import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { api } from '../../api/endpoints'
import type { DemoActionResult, DemoStatus } from '../../api/types'
import { formatDateTime } from '../../lib/format'

// 데모 도구 (/admin/demo, docs/demo-scenario.md 1장). 시계를 앞당기면 같은 스케줄러 코드가 도래한 작업을 바로 실행한다.
export default function AdminDemoPage() {
  const queryClient = useQueryClient()
  const status = useQuery({ queryKey: ['demo-status'], queryFn: api.demoStatus })
  const onDone = (data: DemoStatus) => {
    queryClient.setQueryData(['demo-status'], data)
    // 매칭 결과 등 다른 화면 데이터도 새로 읽는다
    queryClient.invalidateQueries({ predicate: (q) => q.queryKey[0] !== 'demo-status' })
  }
  const reset = useMutation({ mutationFn: api.demoReset, onSuccess: onDone })
  const jump = useMutation({ mutationFn: api.demoJump, onSuccess: onDone })
  const advance = useMutation({ mutationFn: api.demoAdvance, onSuccess: onDone })
  const [actionMessage, setActionMessage] = useState<string>()
  const onActionDone = (result: DemoActionResult) => {
    setActionMessage(result.message)
    queryClient.invalidateQueries()
  }
  const checkInAll = useMutation({ mutationFn: api.demoCheckInAll, onSuccess: onActionDone })
  const feedbackAll = useMutation({ mutationFn: api.demoFeedbackAll, onSuccess: onActionDone })
  const round2 = useMutation({ mutationFn: api.demoRound2, onSuccess: onActionDone })
  const actions = [
    { label: '체크인 일괄', hint: '1회차 확정자 중 user13 제외 전원 체크인', mutation: checkInAll },
    { label: '피드백 일괄', hint: '체크인한 사람 중 피드백 없는 사람에게 기본 피드백', mutation: feedbackAll },
    { label: '2회차 신청 투입', hint: '1회차 S1 참석자 전원 + user25·26이 S4 신청·결제', mutation: round2 },
  ]
  const busy =
    reset.isPending || jump.isPending || advance.isPending || actions.some((a) => a.mutation.isPending)
  const error = reset.error ?? jump.error ?? advance.error ?? status.error ?? actions.map((a) => a.mutation.error).find(Boolean)

  const offset = status.data?.offsetSeconds ?? 0
  const offsetText = `${Math.floor(offset / 86400)}일 ${Math.floor((offset % 86400) / 3600)}시간 ${Math.floor((offset % 3600) / 60)}분`

  return (
    <div>
      <h1 className="text-2xl font-bold">데모 도구</h1>

      <section className="mt-4 grid gap-3 md:grid-cols-3">
        <Card title="데모 시각">
          <p className="text-xl font-semibold">{formatDateTime(status.data?.now)}</p>
          <p className="text-xs text-gray-500">실제 시각보다 {offsetText} 앞섬</p>
        </Card>
        <Card title="시드">
          <p className="text-xl font-semibold">{status.data?.seedVersion ?? '없음'}</p>
          <p className="text-xs text-gray-500">T0 {formatDateTime(status.data?.t0)}</p>
        </Card>
        <Card title="다음 예정 작업">
          <ul className="space-y-0.5 text-sm">
            {status.data?.upcomingJobs.length === 0 && <li className="text-gray-500">없음</li>}
            {status.data?.upcomingJobs.map((j) => (
              <li key={`${j.jobType}-${j.targetId}`}>
                {j.jobType} #{j.targetId} · {formatDateTime(j.dueAt)}
              </li>
            ))}
          </ul>
        </Card>
      </section>

      {error && <p className="mt-3 text-sm text-red-700">{errorMessage(error)}</p>}

      <section className="mt-4 rounded-lg border border-gray-200 bg-white p-4">
        <h2 className="font-semibold">시나리오 점프</h2>
        <p className="mt-1 text-xs text-gray-500">
          버튼을 누르면 데모 시각이 해당 시점으로 이동하고, 그때까지 도래한 작업(매칭 등)이 바로 실행됩니다. 앞으로만 이동할 수 있어요.
        </p>
        <div className="mt-3 grid gap-2 md:grid-cols-3">
          {status.data?.jumps.map((j) => (
            <button
              key={j.code}
              type="button"
              disabled={busy || j.reached}
              onClick={() => jump.mutate(j.code)}
              className="rounded-lg border border-gray-300 p-3 text-left text-sm hover:bg-amber-50 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
            >
              <span className="font-semibold">⏩ {j.code}</span> {j.label}
              <span className="block text-xs">{formatDateTime(j.at)} {j.reached && '· 지남'}</span>
            </button>
          ))}
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <button type="button" disabled={busy} onClick={() => advance.mutate(60)}
            className="rounded border border-gray-300 px-3 py-2 text-sm disabled:text-gray-400">
            +1시간
          </button>
          <button type="button" disabled={busy} onClick={() => advance.mutate(1440)}
            className="rounded border border-gray-300 px-3 py-2 text-sm disabled:text-gray-400">
            +1일
          </button>
          <Link to="/admin/slots" className="rounded border border-gray-300 px-3 py-2 text-sm">
            회차·매칭 결과 보기 →
          </Link>
        </div>
      </section>

      <section className="mt-4 rounded-lg border border-gray-200 bg-white p-4">
        <h2 className="font-semibold">시연용 일괄 동작</h2>
        <p className="mt-1 text-xs text-gray-500">
          J4(행사 시작) 이후 체크인 일괄, J5(행사 종료) 이후 피드백 일괄, 그 뒤 2회차 신청 투입 순서로 누르세요.
        </p>
        <div className="mt-3 grid gap-2 md:grid-cols-3">
          {actions.map((a) => (
            <button
              key={a.label}
              type="button"
              disabled={busy}
              onClick={() => a.mutation.mutate()}
              className="rounded-lg border border-gray-300 p-3 text-left text-sm hover:bg-amber-50 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
            >
              <span className="font-semibold">{a.label}</span>
              <span className="block text-xs text-gray-500">{a.hint}</span>
            </button>
          ))}
        </div>
        {actionMessage && <p className="mt-2 text-sm text-green-800">{actionMessage}</p>}
      </section>

      <section className="mt-4 rounded-lg border border-red-200 bg-white p-4">
        <h2 className="font-semibold">시드 초기화</h2>
        <p className="mt-1 text-xs text-gray-500">
          모든 데이터를 지우고 demo-v1 시드(참가자 30명·회차 5개)를 다시 넣습니다. 데모 시각도 지금으로 돌아갑니다.
        </p>
        <button
          type="button"
          disabled={busy}
          onClick={() => {
            if (window.confirm('모든 데이터를 지우고 시드를 다시 넣을까요?')) reset.mutate()
          }}
          className="mt-3 rounded bg-red-700 px-4 py-2 text-sm text-white disabled:bg-gray-400"
        >
          {reset.isPending ? '초기화 중…' : '시드 초기화'}
        </button>
      </section>
    </div>
  )
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <p className="text-xs text-gray-500">{title}</p>
      <div className="mt-1">{children}</div>
    </div>
  )
}
